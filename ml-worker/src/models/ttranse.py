import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
from typing import Dict, List, Optional, Tuple


class TTransE(nn.Module):
    def __init__(self, num_entities: int, num_relations: int, num_timestamps: int,
                 embedding_dim: int = 100, margin: float = 1.0, norm: int = 1):
        super().__init__()
        self.embedding_dim = embedding_dim
        self.margin = margin
        self.norm = norm

        self.entity_embeddings = nn.Embedding(num_entities, embedding_dim)
        self.relation_embeddings = nn.Embedding(num_relations, embedding_dim)
        self.time_embeddings = nn.Embedding(num_timestamps, embedding_dim)

        nn.init.xavier_uniform_(self.entity_embeddings.weight)
        nn.init.xavier_uniform_(self.relation_embeddings.weight)
        nn.init.xavier_uniform_(self.time_embeddings.weight)

        self.relation_embeddings.weight.data = self._normalize(self.relation_embeddings.weight.data)

    def _normalize(self, weights: torch.Tensor) -> torch.Tensor:
        return weights / (weights.norm(p=self.norm, dim=1, keepdim=True) + 1e-8)

    def forward(self, s: torch.Tensor, r: torch.Tensor, o: torch.Tensor,
                t: torch.Tensor) -> torch.Tensor:
        h = self.entity_embeddings(s)
        rel = self.relation_embeddings(r)
        tail = self.entity_embeddings(o)
        t_emb = self.time_embeddings(t)

        score = torch.norm(h + rel + t_emb - tail, p=self.norm, dim=1)
        return score

    def score(self, s: torch.Tensor, r: torch.Tensor, o: torch.Tensor,
              t: torch.Tensor) -> torch.Tensor:
        return -self.forward(s, r, o, t)

    def predict_object(self, s: torch.Tensor, r: torch.Tensor, t: torch.Tensor,
                       all_entities: torch.Tensor, top_k: int = 10) -> Tuple[torch.Tensor, torch.Tensor]:
        h = self.entity_embeddings(s)
        rel = self.relation_embeddings(r)
        t_emb = self.time_embeddings(t)
        target = h + rel + t_emb

        all_entity_embs = self.entity_embeddings(all_entities)
        distances = torch.norm(target.unsqueeze(1) - all_entity_embs, p=self.norm, dim=2)
        scores = -distances.squeeze(0)

        top_scores, top_indices = torch.topk(scores, min(top_k, scores.size(0)))
        return top_indices, top_scores

    def predict_subject(self, r: torch.Tensor, o: torch.Tensor, t: torch.Tensor,
                        all_entities: torch.Tensor, top_k: int = 10) -> Tuple[torch.Tensor, torch.Tensor]:
        tail = self.entity_embeddings(o)
        rel = self.relation_embeddings(r)
        t_emb = self.time_embeddings(t)
        target = tail - rel - t_emb

        all_entity_embs = self.entity_embeddings(all_entities)
        distances = torch.norm(target.unsqueeze(1) - all_entity_embs, p=self.norm, dim=2)
        scores = -distances.squeeze(0)

        top_scores, top_indices = torch.topk(scores, min(top_k, scores.size(0)))
        return top_indices, top_scores
