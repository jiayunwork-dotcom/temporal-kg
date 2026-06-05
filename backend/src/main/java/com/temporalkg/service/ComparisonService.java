package com.temporalkg.service;

import com.temporalkg.dto.ComparisonRequestDTO;
import com.temporalkg.dto.ComparisonResultDTO;
import com.temporalkg.dto.ComparisonResultDTO.*;
import com.temporalkg.entity.GraphEntity;
import com.temporalkg.entity.GraphRelation;
import com.temporalkg.entity.Triple;
import com.temporalkg.repository.GraphEntityRepository;
import com.temporalkg.repository.GraphRelationRepository;
import com.temporalkg.repository.TripleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {

    private final TripleRepository tripleRepository;
    private final GraphEntityRepository entityRepository;
    private final GraphRelationRepository relationRepository;
    private final GraphService graphService;

    public ComparisonResultDTO compare(ComparisonRequestDTO request) {
        OffsetDateTime t1 = graphService.parseTimestamp(request.getT1());
        OffsetDateTime t2 = graphService.parseTimestamp(request.getT2());
        if (t1 == null || t2 == null) {
            throw new IllegalArgumentException("Invalid time parameters");
        }
        if (!t1.isBefore(t2)) {
            throw new IllegalArgumentException("T1 must be before T2");
        }

        List<Triple> snapshotT1 = tripleRepository.findSnapshotAtTime(t1);
        List<Triple> snapshotT2 = tripleRepository.findSnapshotAtTime(t2);

        Map<String, List<Triple>> pairMapT1 = buildPairMap(snapshotT1);
        Map<String, List<Triple>> pairMapT2 = buildPairMap(snapshotT2);

        List<DiffTripleDTO> addedTriples = new ArrayList<>();
        List<DiffTripleDTO> deletedTriples = new ArrayList<>();
        List<DiffTripleDTO> persistedTriples = new ArrayList<>();
        List<DiffTripleDTO> changedTriples = new ArrayList<>();

        Set<Long> involvedEntityIds = new HashSet<>();
        Map<Long, Integer> entityEventCount = new HashMap<>();

        Set<String> allPairs = new HashSet<>();
        allPairs.addAll(pairMapT1.keySet());
        allPairs.addAll(pairMapT2.keySet());

        for (String pairKey : allPairs) {
            List<Triple> t1Triples = pairMapT1.getOrDefault(pairKey, Collections.emptyList());
            List<Triple> t2Triples = pairMapT2.getOrDefault(pairKey, Collections.emptyList());

            boolean pairInT1 = !t1Triples.isEmpty();
            boolean pairInT2 = !t2Triples.isEmpty();

            if (pairInT2 && !pairInT1) {
                for (Triple t : t2Triples) {
                    addedTriples.add(toDiffTriple(t, "ADDED", null));
                    involvedEntityIds.add(t.getSubjectId());
                    involvedEntityIds.add(t.getObjectId());
                    entityEventCount.merge(t.getSubjectId(), 1, Integer::sum);
                    entityEventCount.merge(t.getObjectId(), 1, Integer::sum);
                }
            } else if (pairInT1 && !pairInT2) {
                for (Triple t : t1Triples) {
                    deletedTriples.add(toDiffTriple(t, "DELETED", null));
                    involvedEntityIds.add(t.getSubjectId());
                    involvedEntityIds.add(t.getObjectId());
                    entityEventCount.merge(t.getSubjectId(), 1, Integer::sum);
                    entityEventCount.merge(t.getObjectId(), 1, Integer::sum);
                }
            } else {
                Set<Long> t1RelIds = t1Triples.stream().map(Triple::getRelationId).collect(Collectors.toSet());
                Set<Long> t2RelIds = t2Triples.stream().map(Triple::getRelationId).collect(Collectors.toSet());

                Set<Long> commonRelIds = new HashSet<>(t1RelIds);
                commonRelIds.retainAll(t2RelIds);

                Set<Long> removedRelIds = new HashSet<>(t1RelIds);
                removedRelIds.removeAll(t2RelIds);

                Set<Long> addedRelIds = new HashSet<>(t2RelIds);
                addedRelIds.removeAll(t1RelIds);

                for (Triple t : t1Triples) {
                    if (commonRelIds.contains(t.getRelationId())) {
                        persistedTriples.add(toDiffTriple(t, "PERSISTED", null));
                        involvedEntityIds.add(t.getSubjectId());
                        involvedEntityIds.add(t.getObjectId());
                    }
                }

                boolean hasRelationChange = !removedRelIds.isEmpty() && !addedRelIds.isEmpty();

                if (hasRelationChange) {
                    List<Triple> removedTriples = t1Triples.stream()
                            .filter(t -> removedRelIds.contains(t.getRelationId()))
                            .toList();
                    List<Triple> addedRelTriples = t2Triples.stream()
                            .filter(t -> addedRelIds.contains(t.getRelationId()))
                            .toList();

                    int pairCount = Math.min(removedTriples.size(), addedRelTriples.size());

                    for (int i = 0; i < pairCount; i++) {
                        Triple oldTriple = removedTriples.get(i);
                        Triple newTriple = addedRelTriples.get(i);
                        String oldRelName = getRelationName(oldTriple.getRelationId());
                        changedTriples.add(toDiffTriple(newTriple, "CHANGED", oldRelName));
                        involvedEntityIds.add(newTriple.getSubjectId());
                        involvedEntityIds.add(newTriple.getObjectId());
                        entityEventCount.merge(newTriple.getSubjectId(), 1, Integer::sum);
                        entityEventCount.merge(newTriple.getObjectId(), 1, Integer::sum);
                    }

                    for (int i = pairCount; i < removedTriples.size(); i++) {
                        Triple oldTriple = removedTriples.get(i);
                        deletedTriples.add(toDiffTriple(oldTriple, "DELETED", null));
                        involvedEntityIds.add(oldTriple.getSubjectId());
                        involvedEntityIds.add(oldTriple.getObjectId());
                        entityEventCount.merge(oldTriple.getSubjectId(), 1, Integer::sum);
                        entityEventCount.merge(oldTriple.getObjectId(), 1, Integer::sum);
                    }

                    for (int i = pairCount; i < addedRelTriples.size(); i++) {
                        Triple newTriple = addedRelTriples.get(i);
                        addedTriples.add(toDiffTriple(newTriple, "ADDED", null));
                        involvedEntityIds.add(newTriple.getSubjectId());
                        involvedEntityIds.add(newTriple.getObjectId());
                        entityEventCount.merge(newTriple.getSubjectId(), 1, Integer::sum);
                        entityEventCount.merge(newTriple.getObjectId(), 1, Integer::sum);
                    }
                } else {
                    for (Triple t : t1Triples) {
                        if (removedRelIds.contains(t.getRelationId())) {
                            deletedTriples.add(toDiffTriple(t, "DELETED", null));
                            involvedEntityIds.add(t.getSubjectId());
                            involvedEntityIds.add(t.getObjectId());
                            entityEventCount.merge(t.getSubjectId(), 1, Integer::sum);
                            entityEventCount.merge(t.getObjectId(), 1, Integer::sum);
                        }
                    }
                    for (Triple t : t2Triples) {
                        if (addedRelIds.contains(t.getRelationId())) {
                            addedTriples.add(toDiffTriple(t, "ADDED", null));
                            involvedEntityIds.add(t.getSubjectId());
                            involvedEntityIds.add(t.getObjectId());
                            entityEventCount.merge(t.getSubjectId(), 1, Integer::sum);
                            entityEventCount.merge(t.getObjectId(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        if (request.getFilterEntity() != null && !request.getFilterEntity().isBlank()) {
            String filter = request.getFilterEntity().toLowerCase();
            addedTriples = filterTriples(addedTriples, filter);
            deletedTriples = filterTriples(deletedTriples, filter);
            persistedTriples = filterTriples(persistedTriples, filter);
            changedTriples = filterTriples(changedTriples, filter);
        }
        if (request.getFilterRelation() != null && !request.getFilterRelation().isBlank()) {
            String filter = request.getFilterRelation().toLowerCase();
            addedTriples = filterTriplesByRelation(addedTriples, filter);
            deletedTriples = filterTriplesByRelation(deletedTriples, filter);
            persistedTriples = filterTriplesByRelation(persistedTriples, filter);
            changedTriples = filterTriplesByRelation(changedTriples, filter);
        }

        DiffSummary diffSummary = DiffSummary.builder()
                .addedCount(addedTriples.size())
                .deletedCount(deletedTriples.size())
                .persistedCount(persistedTriples.size())
                .changedCount(changedTriples.size())
                .build();

        double t1Density = computeDensity(snapshotT1);
        double t2Density = computeDensity(snapshotT2);
        double densityChangeRate = 0.0;
        if (t1Density != 0) {
            densityChangeRate = (t2Density - t1Density) / t1Density * 100.0;
        }

        int totalEvents = addedTriples.size() + deletedTriples.size() + changedTriples.size();

        List<EntityActivityDTO> entityActivities = new ArrayList<>();
        for (Long eid : involvedEntityIds) {
            String name = getEntityName(eid);
            int count = entityEventCount.getOrDefault(eid, 0);
            double activity = totalEvents > 0 ? (double) count / totalEvents : 0.0;
            entityActivities.add(EntityActivityDTO.builder()
                    .entityId(eid)
                    .entityName(name)
                    .eventCount(count)
                    .activityScore(activity)
                    .build());
        }
        entityActivities.sort((a, b) -> Double.compare(b.getActivityScore(), a.getActivityScore()));

        double maxActivity = entityActivities.stream()
                .mapToDouble(EntityActivityDTO::getActivityScore)
                .max().orElse(1.0);
        if (maxActivity == 0.0) maxActivity = 1.0;

        Map<Long, Double> activityMap = new HashMap<>();
        for (EntityActivityDTO ea : entityActivities) {
            activityMap.put(ea.getEntityId(), ea.getActivityScore() / maxActivity);
        }

        List<DiffNodeDTO> nodes = involvedEntityIds.stream()
                .map(eid -> DiffNodeDTO.builder()
                        .id(eid)
                        .name(getEntityName(eid))
                        .type(getEntityType(eid))
                        .activity(activityMap.getOrDefault(eid, 0.0))
                        .build())
                .toList();

        TransferMatrixDTO transferMatrix = computeTransferMatrix(snapshotT1, snapshotT2);

        double centralityDrift = computeCentralityDrift(snapshotT1, snapshotT2);

        double communityStability = computeCommunityStability(snapshotT1, snapshotT2);

        EvolutionMetrics metrics = EvolutionMetrics.builder()
                .densityChangeRate(densityChangeRate)
                .t1Density(t1Density)
                .t2Density(t2Density)
                .centralityDrift(centralityDrift)
                .communityStability(communityStability)
                .build();

        return ComparisonResultDTO.builder()
                .diffSummary(diffSummary)
                .evolutionMetrics(metrics)
                .addedTriples(addedTriples)
                .deletedTriples(deletedTriples)
                .persistedTriples(persistedTriples)
                .changedTriples(changedTriples)
                .nodes(nodes)
                .entityActivities(entityActivities)
                .transferMatrix(transferMatrix)
                .build();
    }

    private Map<String, List<Triple>> buildPairMap(List<Triple> triples) {
        Map<String, List<Triple>> map = new LinkedHashMap<>();
        for (Triple t : triples) {
            String key = t.getSubjectId() + "-" + t.getObjectId();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private DiffTripleDTO toDiffTriple(Triple t, String diffType, String oldRelation) {
        return DiffTripleDTO.builder()
                .id(t.getId())
                .subject(getEntityName(t.getSubjectId()))
                .subjectType(getEntityType(t.getSubjectId()))
                .relation(getRelationName(t.getRelationId()))
                .object(getEntityName(t.getObjectId()))
                .objectType(getEntityType(t.getObjectId()))
                .diffType(diffType)
                .oldRelation(oldRelation)
                .build();
    }

    private String getEntityName(Long entityId) {
        return entityRepository.findById(entityId)
                .map(GraphEntity::getName)
                .orElse("Unknown");
    }

    private String getEntityType(Long entityId) {
        return entityRepository.findById(entityId)
                .map(GraphEntity::getEntityType)
                .orElse("UNKNOWN");
    }

    private String getRelationName(Long relationId) {
        return relationRepository.findById(relationId)
                .map(GraphRelation::getName)
                .orElse("Unknown");
    }

    private double computeDensity(List<Triple> snapshot) {
        Set<Long> nodeIds = new HashSet<>();
        for (Triple t : snapshot) {
            nodeIds.add(t.getSubjectId());
            nodeIds.add(t.getObjectId());
        }
        int n = nodeIds.size();
        if (n <= 1) return 0.0;
        long edgeCount = snapshot.size();
        return (double) edgeCount / (n * (n - 1));
    }

    private List<DiffTripleDTO> filterTriples(List<DiffTripleDTO> triples, String filter) {
        return triples.stream()
                .filter(t -> (t.getSubject() != null && t.getSubject().toLowerCase().contains(filter))
                        || (t.getObject() != null && t.getObject().toLowerCase().contains(filter)))
                .toList();
    }

    private List<DiffTripleDTO> filterTriplesByRelation(List<DiffTripleDTO> triples, String filter) {
        return triples.stream()
                .filter(t -> (t.getRelation() != null && t.getRelation().toLowerCase().contains(filter))
                        || (t.getOldRelation() != null && t.getOldRelation().toLowerCase().contains(filter)))
                .toList();
    }

    private TransferMatrixDTO computeTransferMatrix(List<Triple> snapshotT1, List<Triple> snapshotT2) {
        List<GraphRelation> allRelations = relationRepository.findAll();
        List<String> relationTypes = allRelations.stream().map(GraphRelation::getName).sorted().toList();
        Map<String, Integer> relNameToIndex = new HashMap<>();
        for (int i = 0; i < relationTypes.size(); i++) {
            relNameToIndex.put(relationTypes.get(i), i);
        }

        int n = relationTypes.size();
        List<List<Integer>> matrix = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Integer> row = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                row.add(0);
            }
            matrix.add(row);
        }

        Map<String, String> pairRelT1 = new HashMap<>();
        for (Triple t : snapshotT1) {
            String pairKey = t.getSubjectId() + "-" + t.getObjectId();
            pairRelT1.put(pairKey, getRelationName(t.getRelationId()));
        }

        Map<String, String> pairRelT2 = new HashMap<>();
        for (Triple t : snapshotT2) {
            String pairKey = t.getSubjectId() + "-" + t.getObjectId();
            pairRelT2.put(pairKey, getRelationName(t.getRelationId()));
        }

        Set<String> allPairs = new HashSet<>();
        allPairs.addAll(pairRelT1.keySet());
        allPairs.addAll(pairRelT2.keySet());

        for (String pair : allPairs) {
            String rel1 = pairRelT1.get(pair);
            String rel2 = pairRelT2.get(pair);
            if (rel1 == null || rel2 == null) continue;
            Integer idx1 = relNameToIndex.get(rel1);
            Integer idx2 = relNameToIndex.get(rel2);
            if (idx1 != null && idx2 != null) {
                matrix.get(idx1).set(idx2, matrix.get(idx1).get(idx2) + 1);
            }
        }

        return TransferMatrixDTO.builder()
                .relationTypes(relationTypes)
                .matrix(matrix)
                .build();
    }

    private double computeCentralityDrift(List<Triple> snapshotT1, List<Triple> snapshotT2) {
        Map<Long, Integer> degreeT1 = computeDegreeCentrality(snapshotT1);
        Map<Long, Integer> degreeT2 = computeDegreeCentrality(snapshotT2);

        Set<Long> allEntities = new HashSet<>();
        allEntities.addAll(degreeT1.keySet());
        allEntities.addAll(degreeT2.keySet());

        if (allEntities.size() < 2) return 0.0;

        List<Long> entityList = new ArrayList<>(allEntities);
        entityList.sort(Long::compareTo);

        double[] degreeArrT1 = new double[entityList.size()];
        double[] degreeArrT2 = new double[entityList.size()];
        for (int i = 0; i < entityList.size(); i++) {
            degreeArrT1[i] = degreeT1.getOrDefault(entityList.get(i), 0);
            degreeArrT2[i] = degreeT2.getOrDefault(entityList.get(i), 0);
        }

        return kendallTauB(degreeArrT1, degreeArrT2);
    }

    private Map<Long, Integer> computeDegreeCentrality(List<Triple> snapshot) {
        Map<Long, Integer> degree = new HashMap<>();
        for (Triple t : snapshot) {
            degree.merge(t.getSubjectId(), 1, Integer::sum);
            degree.merge(t.getObjectId(), 1, Integer::sum);
        }
        return degree;
    }

    private double kendallTauB(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) return 0.0;

        long concordant = 0;
        long discordant = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double diffX = x[i] - x[j];
                double diffY = y[i] - y[j];
                double product = diffX * diffY;
                if (product > 0) concordant++;
                else if (product < 0) discordant++;
            }
        }

        long n0 = (long) n * (n - 1) / 2;

        Map<Double, Long> tiesX = new HashMap<>();
        Map<Double, Long> tiesY = new HashMap<>();
        for (int i = 0; i < n; i++) {
            tiesX.merge(x[i], 1L, Long::sum);
            tiesY.merge(y[i], 1L, Long::sum);
        }

        long n1 = 0;
        for (long t : tiesX.values()) {
            if (t > 1) n1 += t * (t - 1) / 2;
        }

        long n2 = 0;
        for (long t : tiesY.values()) {
            if (t > 1) n2 += t * (t - 1) / 2;
        }

        double denomX = n0 - n1;
        double denomY = n0 - n2;

        if (denomX <= 0 || denomY <= 0) return 0.0;

        return (concordant - discordant) / Math.sqrt(denomX * denomY);
    }

    private double computeCommunityStability(List<Triple> snapshotT1, List<Triple> snapshotT2) {
        Map<Long, Integer> labelsT1 = computeConnectedComponents(snapshotT1);
        Map<Long, Integer> labelsT2 = computeConnectedComponents(snapshotT2);

        Set<Long> commonEntities = new HashSet<>(labelsT1.keySet());
        commonEntities.retainAll(labelsT2.keySet());

        if (commonEntities.isEmpty()) return 0.0;

        return computeNMI(labelsT1, labelsT2, commonEntities);
    }

    private Map<Long, Integer> computeConnectedComponents(List<Triple> snapshot) {
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        for (Triple t : snapshot) {
            adjacency.computeIfAbsent(t.getSubjectId(), k -> new HashSet<>()).add(t.getObjectId());
            adjacency.computeIfAbsent(t.getObjectId(), k -> new HashSet<>()).add(t.getSubjectId());
        }

        Set<Long> visited = new HashSet<>();
        Map<Long, Integer> labels = new HashMap<>();
        int componentId = 0;

        for (Long entityId : adjacency.keySet()) {
            if (!visited.contains(entityId)) {
                bfsComponent(entityId, adjacency, visited, labels, componentId);
                componentId++;
            }
        }

        return labels;
    }

    private void bfsComponent(Long start, Map<Long, Set<Long>> adjacency, Set<Long> visited,
                              Map<Long, Integer> labels, int componentId) {
        Queue<Long> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        labels.put(start, componentId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            Set<Long> neighbors = adjacency.getOrDefault(current, Set.of());
            for (Long neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    labels.put(neighbor, componentId);
                    queue.add(neighbor);
                }
            }
        }
    }

    private double computeNMI(Map<Long, Integer> labelsT1, Map<Long, Integer> labelsT2, Set<Long> commonEntities) {
        int n = commonEntities.size();
        if (n == 0) return 0.0;

        Map<Integer, Integer> countU = new HashMap<>();
        Map<Integer, Integer> countV = new HashMap<>();
        Map<String, Integer> countUV = new HashMap<>();

        for (Long eid : commonEntities) {
            int u = labelsT1.getOrDefault(eid, -1);
            int v = labelsT2.getOrDefault(eid, -1);
            countU.merge(u, 1, Integer::sum);
            countV.merge(v, 1, Integer::sum);
            countUV.merge(u + "," + v, 1, Integer::sum);
        }

        double hU = 0.0;
        for (int count : countU.values()) {
            double p = (double) count / n;
            if (p > 0) hU -= p * Math.log(p);
        }

        double hV = 0.0;
        for (int count : countV.values()) {
            double p = (double) count / n;
            if (p > 0) hV -= p * Math.log(p);
        }

        double mi = 0.0;
        for (Map.Entry<String, Integer> entry : countUV.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int u = Integer.parseInt(parts[0]);
            int v = Integer.parseInt(parts[1]);
            int uvCount = entry.getValue();
            int uCount = countU.getOrDefault(u, 0);
            int vCount = countV.getOrDefault(v, 0);

            if (uCount > 0 && vCount > 0) {
                double pUV = (double) uvCount / n;
                double pU = (double) uCount / n;
                double pV = (double) vCount / n;
                mi += pUV * Math.log(pUV / (pU * pV));
            }
        }

        double denominator = Math.sqrt(hU * hV);
        if (denominator == 0) return 0.0;
        return mi / denominator;
    }
}
