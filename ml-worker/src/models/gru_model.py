import torch
import torch.nn as nn
import numpy as np
from typing import Dict, List, Optional, Tuple


class GRUTemporalModel(nn.Module):
    def __init__(self, num_entities: int, num_relations: int,
                 embedding_dim: int = 100, hidden_dim: int = 200,
                 num_layers: int = 2, dropout: float = 0.3):
        super().__init__()
        self.embedding_dim = embedding_dim
        self.hidden_dim = hidden_dim
        self.num_entities = num_entities
        self.num_relations = num_relations

        self.entity_embeddings = nn.Embedding(num_entities, embedding_dim)
        self.relation_embeddings = nn.Embedding(num_relations, embedding_dim)

        self.entity_gru = nn.GRU(
            input_size=embedding_dim * 2,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0
        )

        self.score_layer = nn.Sequential(
            nn.Linear(hidden_dim * 2 + embedding_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, 1)
        )

        self.entity_hidden_states: Dict[int, torch.Tensor] = {}
        self.entity_sequences: Dict[int, List[Tuple[torch.Tensor, torch.Tensor]]] = {}

        nn.init.xavier_uniform_(self.entity_embeddings.weight)
        nn.init.xavier_uniform_(self.relation_embeddings.weight)

    def update_entity_state(self, entity_id: int, relation_emb: torch.Tensor,
                            other_entity_emb: torch.Tensor):
        event_input = torch.cat([relation_emb, other_entity_emb]).unsqueeze(0).unsqueeze(0)
        prev_hidden = self.entity_hidden_states.get(entity_id)
        if prev_hidden is not None:
            prev_hidden = prev_hidden.unsqueeze(0)
        _, new_hidden = self.entity_gru(event_input, prev_hidden)
        self.entity_hidden_states[entity_id] = new_hidden.squeeze(0)

    def forward(self, s: torch.Tensor, r: torch.Tensor, o: torch.Tensor) -> torch.Tensor:
        s_emb = self.entity_embeddings(s)
        r_emb = self.relation_embeddings(r)
        o_emb = self.entity_embeddings(o)

        s_hidden = self._get_entity_hidden(s)
        o_hidden = self._get_entity_hidden(o)

        combined = torch.cat([s_hidden, o_hidden, r_emb], dim=1)
        score = self.score_layer(combined).squeeze(1)
        return score

    def _get_entity_hidden(self, entity_ids: torch.Tensor) -> torch.Tensor:
        batch_size = entity_ids.size(0)
        result = torch.zeros(batch_size, self.hidden_dim, device=entity_ids.device)
        for i, eid in enumerate(entity_ids):
            hid = self.entity_hidden_states.get(eid.item())
            if hid is not None:
                result[i] = hid
            else:
                result[i] = self.entity_embeddings(eid)
        return result

    def predict_object(self, s: torch.Tensor, r: torch.Tensor,
                       all_entities: torch.Tensor, top_k: int = 10) -> Tuple[torch.Tensor, torch.Tensor]:
        s_hidden = self._get_entity_hidden(s)
        r_emb = self.relation_embeddings(r)

        all_embs = self.entity_embeddings(all_entities)
        all_hiddens = self._get_entity_hidden(all_entities)

        scores = []
        for i in range(all_entities.size(0)):
            o_emb = all_embs[i].unsqueeze(0).expand(s.size(0), -1)
            o_hidden = all_hiddens[i].unsqueeze(0).expand(s.size(0), -1)
            combined = torch.cat([s_hidden, o_hidden, r_emb], dim=1)
            score = self.score_layer(combined).squeeze(1)
            scores.append(score.mean().item())

        scores_tensor = torch.tensor(scores, device=s.device)
        top_scores, top_indices = torch.topk(scores_tensor, min(top_k, len(scores)))
        return top_indices, top_scores

    def build_temporal_states(self, quadruples: List[Tuple[int, int, int, int]]):
        quadruples_sorted = sorted(quadruples, key=lambda x: x[3])
        for s, r, o, t in quadruples_sorted:
            s_emb = self.entity_embeddings(torch.tensor([s]))
            r_emb = self.relation_embeddings(torch.tensor([r]))
            o_emb = self.entity_embeddings(torch.tensor([o]))

            self.update_entity_state(s, r_emb.detach().squeeze(), o_emb.detach().squeeze())
            self.update_entity_state(o, r_emb.detach().squeeze(), s_emb.detach().squeeze())
