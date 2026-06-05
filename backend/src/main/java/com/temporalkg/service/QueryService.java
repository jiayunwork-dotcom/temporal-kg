package com.temporalkg.service;

import com.temporalkg.dto.*;
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
public class QueryService {

    private final TripleRepository tripleRepository;
    private final GraphEntityRepository entityRepository;
    private final GraphRelationRepository relationRepository;
    private final GraphService graphService;

    private Optional<GraphEntity> findEntityByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        List<GraphEntity> candidates = entityRepository.findAllByName(name);
        if (candidates.isEmpty()) return Optional.empty();
        return candidates.stream()
                .filter(e -> !"UNKNOWN".equals(e.getEntityType()))
                .findFirst()
                .or(() -> Optional.of(candidates.get(0)));
    }

    public List<TripleDTO> basicQuery(String entityName, String relationType, String timeStart, String timeEnd) {
        List<Triple> triples;

        if (entityName != null && !entityName.isBlank()) {
            List<GraphEntity> entities = entityRepository.fuzzySearchByName(entityName, 100);
            List<Long> entityIds = entities.stream().map(GraphEntity::getId).toList();
            if (entityIds.isEmpty()) {
                return List.of();
            }
            triples = tripleRepository.findByEntityIds(entityIds);
        } else {
            triples = tripleRepository.findAll();
        }

        if (relationType != null && !relationType.isBlank()) {
            Optional<GraphRelation> rel = relationRepository.findByName(relationType);
            if (rel.isPresent()) {
                Long relId = rel.get().getId();
                triples = triples.stream().filter(t -> t.getRelationId().equals(relId)).toList();
            }
        }

        if (timeStart != null && !timeStart.isBlank() && timeEnd != null && !timeEnd.isBlank()) {
            OffsetDateTime start = graphService.parseTimestamp(timeStart);
            OffsetDateTime end = graphService.parseTimestamp(timeEnd);
            if (start != null && end != null) {
                triples = triples.stream().filter(t -> isInRange(t, start, end)).toList();
            }
        }

        return triples.stream().map(graphService::toDTO).toList();
    }

    public List<PathDTO> findPaths(String fromEntity, String toEntity, int maxHops, boolean shortestOnly) {
        Optional<GraphEntity> from = findEntityByName(fromEntity);
        Optional<GraphEntity> to = findEntityByName(toEntity);

        if (from.isEmpty() || to.isEmpty()) return List.of();

        Long fromId = from.get().getId();
        Long toId = to.get().getId();

        if (shortestOnly) {
            PathDTO path = bfsShortestPath(fromId, toId, maxHops);
            return path != null ? List.of(path) : List.of();
        } else {
            return dfsAllPaths(fromId, toId, maxHops);
        }
    }

    public SubGraphDTO extractSubGraph(String centerEntity, int hops) {
        Optional<GraphEntity> center = findEntityByName(centerEntity);
        if (center.isEmpty()) return new SubGraphDTO(List.of(), List.of());

        Long centerId = center.get().getId();
        Set<Long> visitedEntityIds = new HashSet<>();
        Set<Long> visitedTripleIds = new HashSet<>();
        List<Triple> collectedTriples = new ArrayList<>();

        Set<Long> currentLevel = new HashSet<>();
        currentLevel.add(centerId);

        for (int h = 0; h < hops; h++) {
            Set<Long> nextLevel = new HashSet<>();
            for (Long entityId : currentLevel) {
                if (visitedEntityIds.contains(entityId)) continue;
                visitedEntityIds.add(entityId);
                List<Triple> neighbors = tripleRepository.findNeighbors(entityId);
                for (Triple t : neighbors) {
                    if (!visitedTripleIds.contains(t.getId())) {
                        visitedTripleIds.add(t.getId());
                        collectedTriples.add(t);
                        if (!visitedEntityIds.contains(t.getSubjectId())) nextLevel.add(t.getSubjectId());
                        if (!visitedEntityIds.contains(t.getObjectId())) nextLevel.add(t.getObjectId());
                    }
                }
            }
            currentLevel = nextLevel;
        }

        List<GraphNodeDTO> nodes = visitedEntityIds.stream()
                .map(id -> entityRepository.findById(id))
                .filter(Optional::isPresent)
                .map(e -> {
                    GraphEntity ge = e.get();
                    int degree = tripleRepository.countDegree(ge.getId());
                    return new GraphNodeDTO(ge.getId(), ge.getName(), ge.getEntityType(), ge.getAttributes(), degree);
                })
                .toList();

        List<GraphEdgeDTO> edges = collectedTriples.stream()
                .map(this::toEdgeDTO)
                .toList();

        return new SubGraphDTO(nodes, edges);
    }

    public SubGraphDTO timeSlice(String timePoint) {
        OffsetDateTime point = graphService.parseTimestamp(timePoint);
        List<Triple> snapshot = tripleRepository.findSnapshotAtTime(point);

        Set<Long> entityIds = new HashSet<>();
        for (Triple t : snapshot) {
            entityIds.add(t.getSubjectId());
            entityIds.add(t.getObjectId());
        }

        List<GraphNodeDTO> nodes = entityIds.stream()
                .map(id -> entityRepository.findById(id))
                .filter(Optional::isPresent)
                .map(e -> {
                    GraphEntity ge = e.get();
                    int degree = tripleRepository.countDegree(ge.getId());
                    return new GraphNodeDTO(ge.getId(), ge.getName(), ge.getEntityType(), ge.getAttributes(), degree);
                })
                .toList();

        List<GraphEdgeDTO> edges = snapshot.stream().map(this::toEdgeDTO).toList();

        return new SubGraphDTO(nodes, edges);
    }

    public List<TripleDTO> entityTimeline(String entityName) {
        Optional<GraphEntity> entity = findEntityByName(entityName);
        if (entity.isEmpty()) return List.of();

        List<Triple> timeline = tripleRepository.findEntityTimeline(entity.get().getId());
        return timeline.stream().map(graphService::toDTO).toList();
    }

    public List<SubGraphDTO> patternMatch(String patternJson) {
        return Collections.emptyList();
    }

    public SubGraphDTO getFullGraph(int limit) {
        List<Triple> triples = tripleRepository.findAll()
                .stream().limit(limit).toList();

        Set<Long> entityIds = new HashSet<>();
        for (Triple t : triples) {
            entityIds.add(t.getSubjectId());
            entityIds.add(t.getObjectId());
        }

        List<GraphNodeDTO> nodes = entityIds.stream()
                .map(id -> entityRepository.findById(id))
                .filter(Optional::isPresent)
                .map(e -> {
                    GraphEntity ge = e.get();
                    int degree = tripleRepository.countDegree(ge.getId());
                    return new GraphNodeDTO(ge.getId(), ge.getName(), ge.getEntityType(), ge.getAttributes(), degree);
                })
                .toList();

        List<GraphEdgeDTO> edges = triples.stream().map(this::toEdgeDTO).toList();

        return new SubGraphDTO(nodes, edges);
    }

    private boolean isInRange(Triple t, OffsetDateTime start, OffsetDateTime end) {
        if (t.getTimePoint() != null) {
            return !t.getTimePoint().isBefore(start) && !t.getTimePoint().isAfter(end);
        }
        if (t.getTimeStart() != null && t.getTimeEnd() != null) {
            return !t.getTimeStart().isAfter(end) && !t.getTimeEnd().isBefore(start);
        }
        return false;
    }

    private PathDTO bfsShortestPath(Long fromId, Long toId, int maxHops) {
        Map<Long, Triple> parent = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(fromId);
        visited.add(fromId);

        while (!queue.isEmpty() && parent.size() < maxHops * 100) {
            Long current = queue.poll();
            List<Triple> neighbors = tripleRepository.findNeighbors(current);

            for (Triple t : neighbors) {
                Long neighbor = t.getSubjectId().equals(current) ? t.getObjectId() : t.getSubjectId();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, t);
                    if (neighbor.equals(toId)) {
                        return reconstructPath(fromId, toId, parent);
                    }
                    queue.add(neighbor);
                }
            }
        }
        return null;
    }

    private List<PathDTO> dfsAllPaths(Long fromId, Long toId, int maxHops) {
        List<PathDTO> paths = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(fromId);
        List<Triple> currentPath = new ArrayList<>();
        dfs(fromId, toId, maxHops, visited, currentPath, paths);
        return paths;
    }

    private void dfs(Long current, Long target, int remainingHops, Set<Long> visited,
                     List<Triple> currentPath, List<PathDTO> results) {
        if (current.equals(target)) {
            results.add(buildPathDTO(currentPath));
            return;
        }
        if (remainingHops <= 0 || results.size() >= 50) return;

        List<Triple> neighbors = tripleRepository.findNeighbors(current);
        for (Triple t : neighbors) {
            Long next = t.getSubjectId().equals(current) ? t.getObjectId() : t.getSubjectId();
            if (!visited.contains(next)) {
                visited.add(next);
                currentPath.add(t);
                dfs(next, target, remainingHops - 1, visited, currentPath, results);
                currentPath.remove(currentPath.size() - 1);
                visited.remove(next);
            }
        }
    }

    private PathDTO reconstructPath(Long fromId, Long toId, Map<Long, Triple> parent) {
        List<Triple> pathTriples = new ArrayList<>();
        Set<Long> entityIds = new HashSet<>();
        Long current = toId;

        while (!current.equals(fromId)) {
            Triple t = parent.get(current);
            if (t == null) break;
            pathTriples.add(t);
            entityIds.add(t.getSubjectId());
            entityIds.add(t.getObjectId());
            current = t.getSubjectId().equals(current) ? t.getObjectId() : t.getSubjectId();
        }

        Collections.reverse(pathTriples);

        List<GraphNodeDTO> nodes = entityIds.stream()
                .map(id -> entityRepository.findById(id))
                .filter(Optional::isPresent)
                .map(e -> new GraphNodeDTO(e.get().getId(), e.get().getName(), e.get().getEntityType(), null, 0))
                .toList();

        List<GraphEdgeDTO> edges = pathTriples.stream().map(this::toEdgeDTO).toList();

        return new PathDTO(nodes, edges, pathTriples.size(), Map.of());
    }

    private PathDTO buildPathDTO(List<Triple> pathTriples) {
        Set<Long> entityIds = new HashSet<>();
        for (Triple t : pathTriples) {
            entityIds.add(t.getSubjectId());
            entityIds.add(t.getObjectId());
        }

        List<GraphNodeDTO> nodes = entityIds.stream()
                .map(id -> entityRepository.findById(id))
                .filter(Optional::isPresent)
                .map(e -> new GraphNodeDTO(e.get().getId(), e.get().getName(), e.get().getEntityType(), null, 0))
                .toList();

        List<GraphEdgeDTO> edges = pathTriples.stream().map(this::toEdgeDTO).toList();

        return new PathDTO(nodes, edges, pathTriples.size(), Map.of());
    }

    public EntityDetailDTO getEntityDetail(Long entityId) {
        Optional<GraphEntity> optEntity = entityRepository.findById(entityId);
        if (optEntity.isEmpty()) return null;

        GraphEntity entity = optEntity.get();
        int degree = tripleRepository.countDegree(entityId);
        List<Triple> timeline = tripleRepository.findEntityTimeline(entityId);

        String earliestTime = null;
        String latestTime = null;
        for (Triple t : timeline) {
            String timeStr = extractTimeString(t);
            if (timeStr == null) continue;
            if (earliestTime == null || timeStr.compareTo(earliestTime) < 0) earliestTime = timeStr;
            if (latestTime == null || timeStr.compareTo(latestTime) > 0) latestTime = timeStr;
        }

        List<Triple> sortedTimeline = new ArrayList<>(timeline);
        sortedTimeline.sort((a, b) -> {
            String ta = extractTimeString(a);
            String tb = extractTimeString(b);
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        List<EntityDetailDTO.EventItem> recentEvents = sortedTimeline.stream()
                .limit(5)
                .map(t -> {
                    GraphRelation rel = relationRepository.findById(t.getRelationId()).orElse(null);
                    String relationName = rel != null ? rel.getName() : "";
                    String otherEntityName;
                    if (t.getSubjectId().equals(entityId)) {
                        GraphEntity obj = entityRepository.findById(t.getObjectId()).orElse(null);
                        otherEntityName = obj != null ? obj.getName() : "";
                    } else {
                        GraphEntity subj = entityRepository.findById(t.getSubjectId()).orElse(null);
                        otherEntityName = subj != null ? subj.getName() : "";
                    }
                    return EntityDetailDTO.EventItem.builder()
                            .relation(relationName)
                            .otherEntityName(otherEntityName)
                            .time(extractTimeString(t))
                            .build();
                })
                .toList();

        return EntityDetailDTO.builder()
                .id(entityId)
                .name(entity.getName())
                .type(entity.getEntityType())
                .attributes(entity.getAttributes())
                .tripleCount(degree)
                .earliestEventTime(earliestTime)
                .latestEventTime(latestTime)
                .recentEvents(recentEvents)
                .build();
    }

    private String extractTimeString(Triple t) {
        if (t.getTimePoint() != null) return t.getTimePoint().toString();
        if (t.getTimeStart() != null) return t.getTimeStart().toString();
        return null;
    }

    private GraphEdgeDTO toEdgeDTO(Triple t) {
        GraphRelation rel = relationRepository.findById(t.getRelationId()).orElse(null);
        return GraphEdgeDTO.builder()
                .id(t.getId())
                .source(t.getSubjectId())
                .target(t.getObjectId())
                .relation(rel != null ? rel.getName() : null)
                .relationCategory(rel != null ? rel.getCategory() : null)
                .timePoint(t.getTimePoint() != null ? t.getTimePoint().toString() : null)
                .timeStart(t.getTimeStart() != null ? t.getTimeStart().toString() : null)
                .timeEnd(t.getTimeEnd() != null ? t.getTimeEnd().toString() : null)
                .confidence(t.getConfidence())
                .weight(1.0)
                .build();
    }
}
