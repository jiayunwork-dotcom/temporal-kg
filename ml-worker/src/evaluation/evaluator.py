import torch
import numpy as np
import json
import logging
from typing import Dict, List, Tuple
from collections import defaultdict

from models.ttranse import TTransE
from models.gru_model import GRUTemporalModel

logger = logging.getLogger(__name__)


class Evaluator:
    def __init__(self, dataset, model_save_dir: str = "/app/models"):
        self.dataset = dataset
        self.model_save_dir = model_save_dir
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

    def evaluate_link_prediction(self, model_type: str) -> Dict:
        logger.info(f"Evaluating link prediction for {model_type}...")

        model_path = f"{self.model_save_dir}/{model_type}_best.pt"
        try:
            checkpoint = torch.load(model_path, map_location=self.device, weights_only=False)
        except FileNotFoundError:
            logger.error(f"Model not found: {model_path}")
            return {'error': 'Model not found'}

        if model_type == 'ttranse':
            return self._evaluate_ttranse(checkpoint)
        elif model_type == 'gru':
            return self._evaluate_gru(checkpoint)
        else:
            return {'error': f'Unknown model type: {model_type}'}

    def _evaluate_ttranse(self, checkpoint: Dict) -> Dict:
        params = checkpoint.get('params', {})
        model = TTransE(
            num_entities=self.dataset.num_entities,
            num_relations=self.dataset.num_relations,
            num_timestamps=self.dataset.num_timestamps,
            embedding_dim=params.get('embedding_dim', 100),
            margin=params.get('margin', 1.0),
            norm=params.get('norm', 1)
        ).to(self.device)
        model.load_state_dict(checkpoint['model_state_dict'])
        model.eval()

        test_data = self.dataset.test_data
        if not test_data:
            return {'error': 'No test data available'}

        all_entities = torch.arange(self.dataset.num_entities, dtype=torch.long, device=self.device)

        ranks_object = []
        ranks_subject = []

        time_distance_buckets = defaultdict(list)
        time_boundaries = [(0, 24, '1d'), (24, 168, '7d'), (168, 720, '30d'), (720, float('inf'), '30d+')]

        with torch.no_grad():
            for idx, (s, r, o, t) in enumerate(test_data):
                s_t = torch.tensor([s], dtype=torch.long, device=self.device)
                r_t = torch.tensor([r], dtype=torch.long, device=self.device)
                o_t = torch.tensor([o], dtype=torch.long, device=self.device)
                t_t = torch.tensor([t], dtype=torch.long, device=self.device)

                top_indices, _ = model.predict_object(s_t, r_t, t_t, all_entities, top_k=self.dataset.num_entities)
                rank_obj = (top_indices == o).nonzero(as_tuple=True)[0]
                rank_val = rank_obj.item() + 1 if len(rank_obj) > 0 else self.dataset.num_entities
                ranks_object.append(rank_val)

                top_indices_s, _ = model.predict_subject(r_t, o_t, t_t, all_entities, top_k=self.dataset.num_entities)
                rank_sub = (top_indices_s == s).nonzero(as_tuple=True)[0]
                rank_val_s = rank_sub.item() + 1 if len(rank_sub) > 0 else self.dataset.num_entities
                ranks_subject.append(rank_val_s)

                time_dist = abs(t - self.dataset.num_timestamps * 0.9)
                for low, high, label in time_boundaries:
                    if low <= time_dist < high:
                        time_distance_buckets[label].append(rank_val)
                        break

                if (idx + 1) % 100 == 0:
                    logger.info(f"Evaluated {idx + 1}/{len(test_data)} test samples")

        all_ranks = ranks_object + ranks_subject
        mrr = np.mean([1.0 / r for r in all_ranks])
        hits_1 = np.mean([1.0 if r <= 1 else 0.0 for r in all_ranks])
        hits_3 = np.mean([1.0 if r <= 3 else 0.0 for r in all_ranks])
        hits_10 = np.mean([1.0 if r <= 10 else 0.0 for r in all_ranks])

        time_aware = {}
        for label, ranks in time_distance_buckets.items():
            if ranks:
                time_aware[label] = {
                    'mrr': float(np.mean([1.0 / r for r in ranks])),
                    'hits@10': float(np.mean([1.0 if r <= 10 else 0.0 for r in ranks]))
                }

        result = {
            'modelType': 'ttranse',
            'mrr': float(mrr),
            'hitsAt1': float(hits_1),
            'hitsAt3': float(hits_3),
            'hitsAt10': float(hits_10),
            'timeAwareMetrics': time_aware,
            'patternMetrics': {},
            'params': params
        }

        logger.info(f"TTransE Evaluation: MRR={mrr:.4f}, H@1={hits_1:.4f}, H@3={hits_3:.4f}, H@10={hits_10:.4f}")
        return result

    def _evaluate_gru(self, checkpoint: Dict) -> Dict:
        params = checkpoint.get('params', {})
        model = GRUTemporalModel(
            num_entities=self.dataset.num_entities,
            num_relations=self.dataset.num_relations,
            embedding_dim=params.get('embedding_dim', 100),
            hidden_dim=params.get('hidden_dim', 200),
            num_layers=params.get('num_layers', 2),
            dropout=0.0
        ).to(self.device)
        model.load_state_dict(checkpoint['model_state_dict'])
        model.build_temporal_states(self.dataset.train_data + self.dataset.valid_data)
        model.eval()

        test_data = self.dataset.test_data
        if not test_data:
            return {'error': 'No test data available'}

        all_entities = torch.arange(self.dataset.num_entities, dtype=torch.long, device=self.device)
        ranks = []

        with torch.no_grad():
            for idx, (s, r, o, t) in enumerate(test_data):
                s_t = torch.tensor([s], dtype=torch.long, device=self.device)
                r_t = torch.tensor([r], dtype=torch.long, device=self.device)

                top_indices, _ = model.predict_object(s_t, r_t, all_entities, top_k=min(100, self.dataset.num_entities))
                rank = (top_indices == o).nonzero(as_tuple=True)[0]
                rank_val = rank.item() + 1 if len(rank) > 0 else 100
                ranks.append(min(rank_val, 100))

                if (idx + 1) % 100 == 0:
                    logger.info(f"Evaluated {idx + 1}/{len(test_data)} test samples")

        mrr = np.mean([1.0 / r for r in ranks])
        hits_1 = np.mean([1.0 if r <= 1 else 0.0 for r in ranks])
        hits_3 = np.mean([1.0 if r <= 3 else 0.0 for r in ranks])
        hits_10 = np.mean([1.0 if r <= 10 else 0.0 for r in ranks])

        result = {
            'modelType': 'gru',
            'mrr': float(mrr),
            'hitsAt1': float(hits_1),
            'hitsAt3': float(hits_3),
            'hitsAt10': float(hits_10),
            'timeAwareMetrics': {},
            'patternMetrics': {},
            'params': params
        }

        logger.info(f"GRU Evaluation: MRR={mrr:.4f}, H@1={hits_1:.4f}, H@3={hits_3:.4f}, H@10={hits_10:.4f}")
        return result

    def evaluate_patterns(self, patterns: List[Dict], test_data: List[Tuple]) -> Dict:
        triggered = 0
        total = len(patterns)

        for pattern in patterns:
            antecedent = json.loads(pattern['antecedent'])
            consequent = json.loads(pattern['consequent'])
            ant_rel = antecedent.get('relation')

            ant_found = False
            for s, r, o, t in test_data:
                rel_name = self.dataset.id2relation.get(r, '')
                if rel_name == ant_rel:
                    ant_found = True
                    break

            if ant_found:
                triggered += 1

        trigger_rate = triggered / total if total > 0 else 0
        return {
            'total_patterns': total,
            'triggered_on_test': triggered,
            'trigger_rate': float(trigger_rate)
        }
