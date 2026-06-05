import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import json
import psycopg2
import redis
import os
import logging
from typing import Dict, List, Tuple, Optional
from datetime import datetime

from models.ttranse import TTransE
from models.gru_model import GRUTemporalModel

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class TemporalKGDataset:
    def __init__(self, db_config: Dict):
        self.db_config = db_config
        self.entity2id: Dict[str, int] = {}
        self.relation2id: Dict[str, int] = {}
        self.id2entity: Dict[int, str] = {}
        self.id2relation: Dict[int, str] = {}
        self.timestamp2id: Dict[str, int] = {}
        self.id2timestamp: Dict[int, str] = {}
        self.quadruples: List[Tuple[int, int, int, int]] = []
        self.train_data: List[Tuple[int, int, int, int]] = []
        self.valid_data: List[Tuple[int, int, int, int]] = []
        self.test_data: List[Tuple[int, int, int, int]] = []

    def load_from_db(self):
        conn = psycopg2.connect(**self.db_config)
        cur = conn.cursor()

        cur.execute("SELECT id, name FROM entities ORDER BY id")
        for row in cur.fetchall():
            eid, name = row
            self.entity2id[name] = eid
            self.id2entity[eid] = name

        cur.execute("SELECT id, name FROM relations ORDER BY id")
        for row in cur.fetchall():
            rid, name = row
            self.relation2id[name] = rid
            self.id2relation[rid] = name

        cur.execute("""
            SELECT t.subject_id, t.relation_id, t.object_id,
                   COALESCE(t.time_point, t.time_start) as ts
            FROM triples t
            WHERE COALESCE(t.time_point, t.time_start) IS NOT NULL
            ORDER BY COALESCE(t.time_point, t.time_start)
        """)

        timestamps = set()
        for row in cur.fetchall():
            sid, rid, oid, ts = row
            ts_str = ts.isoformat() if ts else None
            if ts_str:
                timestamps.add(ts_str)
            self.quadruples.append((sid, rid, oid, 0))

        sorted_ts = sorted(timestamps)
        for i, ts in enumerate(sorted_ts):
            self.timestamp2id[ts] = i
            self.id2timestamp[i] = ts

        cur.execute("""
            SELECT t.subject_id, t.relation_id, t.object_id,
                   COALESCE(t.time_point, t.time_start) as ts
            FROM triples t
            WHERE COALESCE(t.time_point, t.time_start) IS NOT NULL
            ORDER BY COALESCE(t.time_point, t.time_start)
        """)
        self.quadruples = []
        for row in cur.fetchall():
            sid, rid, oid, ts = row
            ts_str = ts.isoformat() if ts else None
            tid = self.timestamp2id.get(ts_str, 0) if ts_str else 0
            self.quadruples.append((sid, rid, oid, tid))

        n = len(self.quadruples)
        train_end = int(n * 0.8)
        valid_end = int(n * 0.9)
        self.train_data = self.quadruples[:train_end]
        self.valid_data = self.quadruples[train_end:valid_end]
        self.test_data = self.quadruples[valid_end:]

        cur.close()
        conn.close()

        logger.info(f"Loaded {len(self.quadruples)} quadruples: train={len(self.train_data)}, "
                     f"valid={len(self.valid_data)}, test={len(self.test_data)}")
        logger.info(f"Entities: {len(self.entity2id)}, Relations: {len(self.relation2id)}, "
                     f"Timestamps: {len(self.timestamp2id)}")

    @property
    def num_entities(self):
        return len(self.entity2id)

    @property
    def num_relations(self):
        return len(self.relation2id)

    @property
    def num_timestamps(self):
        return max(len(self.timestamp2id), 1)


class NegativeSampler:
    def __init__(self, num_entities: int, num_neg: int = 10):
        self.num_entities = num_entities
        self.num_neg = num_neg

    def sample(self, quadruples: List[Tuple[int, int, int, int]]) -> List[Tuple[int, int, int, int]]:
        neg_samples = []
        for s, r, o, t in quadruples:
            for _ in range(self.num_neg):
                if np.random.random() < 0.5:
                    neg_s = np.random.randint(0, self.num_entities)
                    neg_samples.append((neg_s, r, o, t))
                else:
                    neg_o = np.random.randint(0, self.num_entities)
                    neg_samples.append((s, r, neg_o, t))
        return neg_samples


class Trainer:
    def __init__(self, dataset: TemporalKGDataset, redis_client: redis.Redis,
                 result_queue: str, model_save_dir: str = "/app/models"):
        self.dataset = dataset
        self.redis_client = redis_client
        self.result_queue = result_queue
        self.model_save_dir = model_save_dir
        os.makedirs(model_save_dir, exist_ok=True)
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.sampler = NegativeSampler(dataset.num_entities, num_neg=10)

    def train_ttranse(self, params: Dict) -> Dict:
        logger.info("Starting TTransE training...")
        embedding_dim = params.get('embedding_dim', 100)
        margin = params.get('margin', 1.0)
        lr = params.get('learning_rate', 0.01)
        epochs = params.get('epochs', 100)
        batch_size = params.get('batch_size', 512)
        norm = params.get('norm', 1)

        model = TTransE(
            num_entities=self.dataset.num_entities,
            num_relations=self.dataset.num_relations,
            num_timestamps=self.dataset.num_timestamps,
            embedding_dim=embedding_dim,
            margin=margin,
            norm=norm
        ).to(self.device)

        optimizer = optim.Adam(model.parameters(), lr=lr)
        scheduler = optim.lr_scheduler.StepLR(optimizer, step_size=20, gamma=0.5)

        best_valid_loss = float('inf')
        patience = 0
        max_patience = params.get('patience', 10)

        for epoch in range(epochs):
            model.train()
            total_loss = 0
            train_data = self.dataset.train_data

            np.random.shuffle(train_data)
            neg_data = self.sampler.sample(train_data)

            for i in range(0, len(train_data), batch_size):
                batch_pos = train_data[i:i + batch_size]
                batch_neg = neg_data[i * self.sampler.num_neg:(i + batch_size) * self.sampler.num_neg]

                if not batch_pos:
                    continue

                pos_s = torch.tensor([x[0] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_r = torch.tensor([x[1] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_o = torch.tensor([x[2] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_t = torch.tensor([x[3] for x in batch_pos], dtype=torch.long, device=self.device)

                neg_s = torch.tensor([x[0] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_r = torch.tensor([x[1] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_o = torch.tensor([x[2] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_t = torch.tensor([x[3] for x in batch_neg], dtype=torch.long, device=self.device)

                pos_scores = model(pos_s, pos_r, pos_o, pos_t)
                neg_scores = model(neg_s, neg_r, neg_o, neg_t)

                loss = torch.relu(margin + pos_scores.mean() - neg_scores.mean())

                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
                total_loss += loss.item()

            scheduler.step()

            valid_loss = self._validate_ttranse(model, batch_size)
            logger.info(f"Epoch {epoch + 1}/{epochs}, Train Loss: {total_loss:.4f}, Valid Loss: {valid_loss:.4f}")

            if valid_loss < best_valid_loss:
                best_valid_loss = valid_loss
                patience = 0
                torch.save({
                    'model_state_dict': model.state_dict(),
                    'entity2id': self.dataset.entity2id,
                    'relation2id': self.dataset.relation2id,
                    'timestamp2id': self.dataset.timestamp2id,
                    'params': params
                }, os.path.join(self.model_save_dir, 'ttranse_best.pt'))
            else:
                patience += 1
                if patience >= max_patience:
                    logger.info(f"Early stopping at epoch {epoch + 1}")
                    break

        return {'model': 'ttranse', 'best_valid_loss': float(best_valid_loss), 'epochs_trained': epoch + 1}

    def train_gru(self, params: Dict) -> Dict:
        logger.info("Starting GRU Temporal Model training...")
        embedding_dim = params.get('embedding_dim', 100)
        hidden_dim = params.get('hidden_dim', 200)
        lr = params.get('learning_rate', 0.001)
        epochs = params.get('epochs', 50)
        batch_size = params.get('batch_size', 256)
        num_layers = params.get('num_layers', 2)
        dropout = params.get('dropout', 0.3)

        model = GRUTemporalModel(
            num_entities=self.dataset.num_entities,
            num_relations=self.dataset.num_relations,
            embedding_dim=embedding_dim,
            hidden_dim=hidden_dim,
            num_layers=num_layers,
            dropout=dropout
        ).to(self.device)

        model.build_temporal_states(self.dataset.train_data)

        optimizer = optim.Adam(model.parameters(), lr=lr)
        criterion = nn.BCEWithLogitsLoss()

        best_valid_loss = float('inf')
        patience = 0
        max_patience = params.get('patience', 5)

        for epoch in range(epochs):
            model.train()
            total_loss = 0
            train_data = self.dataset.train_data
            np.random.shuffle(train_data)

            for i in range(0, len(train_data), batch_size):
                batch = train_data[i:i + batch_size]
                if not batch:
                    continue

                s = torch.tensor([x[0] for x in batch], dtype=torch.long, device=self.device)
                r = torch.tensor([x[1] for x in batch], dtype=torch.long, device=self.device)
                o = torch.tensor([x[2] for x in batch], dtype=torch.long, device=self.device)

                pos_scores = model(s, r, o)
                pos_labels = torch.ones_like(pos_scores)

                neg_indices = np.random.randint(0, self.dataset.num_entities, size=len(batch))
                neg_o = torch.tensor(neg_indices, dtype=torch.long, device=self.device)
                neg_scores = model(s, r, neg_o)
                neg_labels = torch.zeros_like(neg_scores)

                all_scores = torch.cat([pos_scores, neg_scores])
                all_labels = torch.cat([pos_labels, neg_labels])

                loss = criterion(all_scores, all_labels)

                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                optimizer.step()
                total_loss += loss.item()

            valid_loss = self._validate_gru(model, batch_size)
            logger.info(f"Epoch {epoch + 1}/{epochs}, Train Loss: {total_loss:.4f}, Valid Loss: {valid_loss:.4f}")

            if valid_loss < best_valid_loss:
                best_valid_loss = valid_loss
                patience = 0
                torch.save({
                    'model_state_dict': model.state_dict(),
                    'entity_hidden_states': {k: v.cpu() for k, v in model.entity_hidden_states.items()},
                    'entity2id': self.dataset.entity2id,
                    'relation2id': self.dataset.relation2id,
                    'params': params
                }, os.path.join(self.model_save_dir, 'gru_best.pt'))
            else:
                patience += 1
                if patience >= max_patience:
                    logger.info(f"Early stopping at epoch {epoch + 1}")
                    break

        return {'model': 'gru', 'best_valid_loss': float(best_valid_loss), 'epochs_trained': epoch + 1}

    def _validate_ttranse(self, model: TTransE, batch_size: int) -> float:
        model.eval()
        total_loss = 0
        with torch.no_grad():
            valid_data = self.dataset.valid_data
            neg_data = self.sampler.sample(valid_data)

            for i in range(0, len(valid_data), batch_size):
                batch_pos = valid_data[i:i + batch_size]
                batch_neg = neg_data[i * self.sampler.num_neg:(i + batch_size) * self.sampler.num_neg]

                if not batch_pos:
                    continue

                pos_s = torch.tensor([x[0] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_r = torch.tensor([x[1] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_o = torch.tensor([x[2] for x in batch_pos], dtype=torch.long, device=self.device)
                pos_t = torch.tensor([x[3] for x in batch_pos], dtype=torch.long, device=self.device)

                neg_s = torch.tensor([x[0] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_r = torch.tensor([x[1] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_o = torch.tensor([x[2] for x in batch_neg], dtype=torch.long, device=self.device)
                neg_t = torch.tensor([x[3] for x in batch_neg], dtype=torch.long, device=self.device)

                pos_scores = model(pos_s, pos_r, pos_o, pos_t)
                neg_scores = model(neg_s, neg_r, neg_o, neg_t)

                loss = torch.relu(model.margin + pos_scores.mean() - neg_scores.mean())
                total_loss += loss.item()

        return total_loss / max(len(valid_data) // batch_size, 1)

    def _validate_gru(self, model: GRUTemporalModel, batch_size: int) -> float:
        model.eval()
        total_loss = 0
        criterion = nn.BCEWithLogitsLoss()
        with torch.no_grad():
            valid_data = self.dataset.valid_data
            for i in range(0, len(valid_data), batch_size):
                batch = valid_data[i:i + batch_size]
                if not batch:
                    continue

                s = torch.tensor([x[0] for x in batch], dtype=torch.long, device=self.device)
                r = torch.tensor([x[1] for x in batch], dtype=torch.long, device=self.device)
                o = torch.tensor([x[2] for x in batch], dtype=torch.long, device=self.device)

                pos_scores = model(s, r, o)
                pos_labels = torch.ones_like(pos_scores)

                neg_indices = np.random.randint(0, self.dataset.num_entities, size=len(batch))
                neg_o = torch.tensor(neg_indices, dtype=torch.long, device=self.device)
                neg_scores = model(s, r, neg_o)
                neg_labels = torch.zeros_like(neg_scores)

                all_scores = torch.cat([pos_scores, neg_scores])
                all_labels = torch.cat([pos_labels, neg_labels])
                loss = criterion(all_scores, all_labels)
                total_loss += loss.item()

        return total_loss / max(len(valid_data) // batch_size, 1)

    def incremental_train(self, model_type: str, params: Dict) -> Dict:
        logger.info(f"Starting incremental training for {model_type}...")
        epochs = params.get('epochs', 5)

        model_path = os.path.join(self.model_save_dir, f'{model_type}_best.pt')
        if not os.path.exists(model_path):
            logger.warning(f"No existing model found for incremental training: {model_path}")
            if model_type == 'ttranse':
                return self.train_ttranse(params)
            else:
                return self.train_gru(params)

        checkpoint = torch.load(model_path, map_location=self.device, weights_only=False)
        saved_params = checkpoint.get('params', params)

        if model_type == 'ttranse':
            model = TTransE(
                num_entities=max(self.dataset.num_entities, checkpoint.get('entity2id', {}).__len__()),
                num_relations=max(self.dataset.num_relations, checkpoint.get('relation2id', {}).__len__()),
                num_timestamps=max(self.dataset.num_timestamps, 1),
                embedding_dim=saved_params.get('embedding_dim', 100),
                margin=saved_params.get('margin', 1.0),
            ).to(self.device)
            model.load_state_dict(checkpoint['model_state_dict'])
        else:
            model = GRUTemporalModel(
                num_entities=max(self.dataset.num_entities, checkpoint.get('entity2id', {}).__len__()),
                num_relations=max(self.dataset.num_relations, checkpoint.get('relation2id', {}).__len__()),
                embedding_dim=saved_params.get('embedding_dim', 100),
                hidden_dim=saved_params.get('hidden_dim', 200),
            ).to(self.device)
            model.load_state_dict(checkpoint['model_state_dict'])

        logger.info(f"Loaded existing {model_type} model, fine-tuning for {epochs} epochs")
        if model_type == 'ttranse':
            params['epochs'] = epochs
            return self.train_ttranse(params)
        else:
            params['epochs'] = epochs
            return self.train_gru(params)
