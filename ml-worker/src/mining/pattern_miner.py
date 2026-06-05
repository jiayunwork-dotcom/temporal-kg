import numpy as np
import torch
import json
import logging
from typing import Dict, List, Tuple
from collections import defaultdict
from itertools import combinations

logger = logging.getLogger(__name__)


class FrequentPatternMiner:
    def __init__(self, min_support: float = 0.01, min_confidence: float = 0.5,
                 max_time_gap_hours: float = 720):
        self.min_support = min_support
        self.min_confidence = min_confidence
        self.max_time_gap_hours = max_time_gap_hours
        self.entity_events: Dict[int, List[Tuple[int, int, int, float]]] = defaultdict(list)
        self.relation_events: Dict[int, List[Tuple[int, int, int, float]]] = defaultdict(list)

    def build_event_sequences(self, quadruples: List[Tuple[int, int, int, int]],
                              id2timestamp: Dict[int, str]):
        self.entity_events.clear()
        self.relation_events.clear()

        for s, r, o, t in quadruples:
            ts_str = id2timestamp.get(t)
            if ts_str is None:
                continue
            ts = float(t)
            self.entity_events[s].append((s, r, o, ts))
            self.entity_events[o].append((s, r, o, ts))
            self.relation_events[r].append((s, r, o, ts))

    def mine_frequent_pairs(self, quadruples: List[Tuple[int, int, int, int]],
                            id2timestamp: Dict[int, str],
                            id2relation: Dict[int, str]) -> List[Dict]:
        self.build_event_sequences(quadruples, id2timestamp)

        pair_counts: Dict[Tuple[int, int], int] = defaultdict(int)
        antecedent_counts: Dict[int, int] = defaultdict(int)
        time_gaps: Dict[Tuple[int, int], List[float]] = defaultdict(list)

        total_events = len(quadruples)

        for entity_id, events in self.entity_events.items():
            events_sorted = sorted(events, key=lambda x: x[3])

            for i in range(len(events_sorted)):
                ant_rel = events_sorted[i][1]
                antecedent_counts[ant_rel] += 1

                for j in range(i + 1, len(events_sorted)):
                    time_gap = events_sorted[j][3] - events_sorted[i][3]
                    if time_gap > self.max_time_gap_hours:
                        break

                    cons_rel = events_sorted[j][1]
                    pair_counts[(ant_rel, cons_rel)] += 1
                    time_gaps[(ant_rel, cons_rel)].append(time_gap)

        patterns = []
        for (ant_rel, cons_rel), count in pair_counts.items():
            support = count / total_events if total_events > 0 else 0
            if support < self.min_support:
                continue

            ant_count = antecedent_counts.get(ant_rel, 0)
            confidence = count / ant_count if ant_count > 0 else 0
            if confidence < self.min_confidence:
                continue

            avg_gap = np.mean(time_gaps[(ant_rel, cons_rel)]) if time_gaps[(ant_rel, cons_rel)] else 0

            ant_name = id2relation.get(ant_rel, str(ant_rel))
            cons_name = id2relation.get(cons_rel, str(cons_rel))

            patterns.append({
                'antecedent': json.dumps({'relation': ant_name}),
                'consequent': json.dumps({'relation': cons_name}),
                'support': float(support),
                'confidence': float(confidence),
                'avg_time_interval_hours': float(avg_gap),
                'pattern_type': 'PAIR'
            })

        patterns.sort(key=lambda x: x['confidence'], reverse=True)
        logger.info(f"Mined {len(patterns)} frequent temporal pairs")
        return patterns

    def mine_frequent_chains(self, quadruples: List[Tuple[int, int, int, int]],
                             id2timestamp: Dict[int, str],
                             id2relation: Dict[int, str],
                             max_chain_length: int = 3) -> List[Dict]:
        pairs = self.mine_frequent_pairs(quadruples, id2timestamp, id2relation)
        if not pairs or max_chain_length < 3:
            return pairs

        chain_patterns = list(pairs)

        relation_events_sorted = {}
        for rel_id, events in self.relation_events.items():
            relation_events_sorted[rel_id] = sorted(events, key=lambda x: x[3])

        for pair in pairs[:50]:
            ant_rel_name = json.loads(pair['antecedent'])['relation']
            cons_rel_name = json.loads(pair['consequent'])['relation']

            ant_rel_id = None
            cons_rel_id = None
            for rid, rname in id2relation.items():
                if rname == ant_rel_name:
                    ant_rel_id = rid
                if rname == cons_rel_name:
                    cons_rel_id = rid

            if ant_rel_id is None or cons_rel_id is None:
                continue

            for third_rel_id, third_rel_name in id2relation.items():
                triple_count = 0
                ant_count = 0
                gaps = []

                for entity_id, events in self.entity_events.items():
                    events_sorted = sorted(events, key=lambda x: x[3])
                    for i in range(len(events_sorted) - 2):
                        if events_sorted[i][1] == ant_rel_id:
                            ant_count += 1
                            for j in range(i + 1, len(events_sorted) - 1):
                                if events_sorted[j][1] == cons_rel_id:
                                    for k in range(j + 1, len(events_sorted)):
                                        if events_sorted[k][1] == third_rel_id:
                                            gap = events_sorted[k][3] - events_sorted[i][3]
                                            if gap <= self.max_time_gap_hours:
                                                triple_count += 1
                                                gaps.append(gap)
                                            break
                                    break

                if ant_count > 0:
                    support = triple_count / len(quadruples) if quadruples else 0
                    confidence = triple_count / ant_count
                    if support >= self.min_support and confidence >= self.min_confidence:
                        chain_patterns.append({
                            'antecedent': json.dumps({'relation': f"{ant_rel_name}→{cons_rel_name}"}),
                            'consequent': json.dumps({'relation': third_rel_name}),
                            'support': float(support),
                            'confidence': float(confidence),
                            'avg_time_interval_hours': float(np.mean(gaps)) if gaps else 0,
                            'pattern_type': 'CHAIN'
                        })

        chain_patterns.sort(key=lambda x: x['confidence'], reverse=True)
        logger.info(f"Mined {len(chain_patterns)} total patterns (pairs + chains)")
        return chain_patterns


class CausalDiscovery:
    def __init__(self, significance_level: float = 0.05, max_lag: int = 10):
        self.significance_level = significance_level
        self.max_lag = max_lag

    def granger_test(self, cause_series: List[float], effect_series: List[float],
                     max_lag: int = None) -> Dict:
        try:
            from statsmodels.tsa.stattools import grangercausalitytests
            import pandas as pd

            if max_lag is None:
                max_lag = self.max_lag

            min_len = min(len(cause_series), len(effect_series))
            if min_len < max_lag + 2:
                return {'significant': False, 'p_value': 1.0, 'f_statistic': 0.0}

            data = pd.DataFrame({
                'effect': effect_series[:min_len],
                'cause': cause_series[:min_len]
            })

            try:
                result = grangercausalitytests(data, maxlag=min(max_lag, min_len - 2), verbose=False)
            except Exception:
                return {'significant': False, 'p_value': 1.0, 'f_statistic': 0.0}

            min_p = 1.0
            best_f = 0.0
            best_lag = 0
            for lag, test_results in result.items():
                ssr_ftest = test_results[0]['ssr_ftest']
                p_val = ssr_ftest[1]
                f_stat = ssr_ftest[0]
                if p_val < min_p:
                    min_p = p_val
                    best_f = f_stat
                    best_lag = lag

            return {
                'significant': min_p < self.significance_level,
                'p_value': float(min_p),
                'f_statistic': float(best_f),
                'best_lag': best_lag
            }
        except ImportError:
            logger.warning("statsmodels not available for Granger test")
            return {'significant': False, 'p_value': 1.0, 'f_statistic': 0.0}

    def discover_causal_links(self, relation_time_series: Dict[int, List[float]],
                              id2relation: Dict[int, str]) -> List[Dict]:
        causal_links = []
        relations = list(relation_time_series.keys())

        for i, cause_rel in enumerate(relations):
            for j, effect_rel in enumerate(relations):
                if i == j:
                    continue

                cause_series = relation_time_series[cause_rel]
                effect_series = relation_time_series[effect_rel]

                if len(cause_series) < self.max_lag + 2:
                    continue

                result = self.granger_test(cause_series, effect_series)
                if result['significant']:
                    causal_links.append({
                        'cause_relation': id2relation.get(cause_rel, str(cause_rel)),
                        'effect_relation': id2relation.get(effect_rel, str(effect_rel)),
                        'granger_f_statistic': result['f_statistic'],
                        'granger_p_value': result['p_value'],
                        'lag': result['best_lag']
                    })

        causal_links.sort(key=lambda x: x['granger_p_value'])
        logger.info(f"Discovered {len(causal_links)} causal links")
        return causal_links

    def build_relation_time_series(self, quadruples: List[Tuple[int, int, int, int]],
                                   num_timestamps: int) -> Dict[int, List[float]]:
        series: Dict[int, List[float]] = defaultdict(lambda: [0.0] * num_timestamps)

        for s, r, o, t in quadruples:
            if t < num_timestamps:
                series[r][t] += 1.0

        return dict(series)
