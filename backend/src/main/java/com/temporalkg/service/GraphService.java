package com.temporalkg.service;

import com.temporalkg.dto.EventRecordDTO;
import com.temporalkg.dto.TripleDTO;
import com.temporalkg.entity.GraphEntity;
import com.temporalkg.entity.GraphRelation;
import com.temporalkg.entity.Triple;
import com.temporalkg.repository.GraphEntityRepository;
import com.temporalkg.repository.GraphRelationRepository;
import com.temporalkg.repository.TripleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphService {

    private final GraphEntityRepository entityRepository;
    private final GraphRelationRepository relationRepository;
    private final TripleRepository tripleRepository;

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final ConcurrentHashMap<String, GraphEntity> entityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GraphRelation> relationCache = new ConcurrentHashMap<>();

    public OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return OffsetDateTime.parse(timestamp, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot parse timestamp: " + timestamp);
        }
    }

    @Transactional
    public GraphEntity getOrCreateEntity(String name, String type, Map<String, Object> attributes) {
        String key = name + "||" + type;
        GraphEntity cached = entityCache.get(key);
        if (cached != null) return cached;

        Optional<GraphEntity> existing = entityRepository.findByNameAndEntityType(name, type);
        if (existing.isPresent()) {
            GraphEntity entity = existing.get();
            if (attributes != null && !attributes.isEmpty()) {
                Map<String, Object> merged = new HashMap<>(entity.getAttributes());
                merged.putAll(attributes);
                entity.setAttributes(merged);
                entity.setUpdatedAt(OffsetDateTime.now());
                entityRepository.save(entity);
            }
            entityCache.put(key, entity);
            return entity;
        }

        GraphEntity entity = GraphEntity.builder()
                .name(name)
                .entityType(type != null ? type : "UNKNOWN")
                .attributes(attributes != null ? attributes : Map.of())
                .build();
        entity = entityRepository.save(entity);
        entityCache.put(key, entity);
        return entity;
    }

    @Transactional
    public GraphRelation getOrCreateRelation(String name, String category) {
        String key = name;
        GraphRelation cached = relationCache.get(key);
        if (cached != null) return cached;

        Optional<GraphRelation> existing = relationRepository.findByName(name);
        if (existing.isPresent()) {
            GraphRelation rel = existing.get();
            relationCache.put(key, rel);
            return rel;
        }

        GraphRelation rel = GraphRelation.builder()
                .name(name)
                .category(category != null ? category : "OTHER")
                .build();
        rel = relationRepository.save(rel);
        relationCache.put(key, rel);
        return rel;
    }

    @Transactional
    public Triple addTriple(EventRecordDTO event) {
        GraphEntity subject = getOrCreateEntity(event.getSubject(), event.getSubjectType(), event.getSubjectAttributes());
        GraphEntity object = getOrCreateEntity(event.getObject(), event.getObjectType(), event.getObjectAttributes());
        GraphRelation relation = getOrCreateRelation(event.getRelation(), event.getRelationCategory());

        if (tripleRepository.countDuplicate(subject.getId(), relation.getId(), object.getId()) > 0) {
            log.debug("Duplicate triple skipped: {} - {} - {}", event.getSubject(), event.getRelation(), event.getObject());
            return null;
        }

        Triple.TripleBuilder builder = Triple.builder()
                .subjectId(subject.getId())
                .relationId(relation.getId())
                .objectId(object.getId())
                .confidence(event.getConfidence() != null ? event.getConfidence() : 1.0)
                .source(event.getSource());

        OffsetDateTime timePoint = parseTimestamp(event.getTimestamp());
        if (timePoint != null) {
            if (event.getTimeEnd() != null) {
                builder.timeStart(timePoint);
                builder.timeEnd(parseTimestamp(event.getTimeEnd()));
            } else {
                builder.timePoint(timePoint);
            }
        }

        return tripleRepository.save(builder.build());
    }

    public List<GraphEntity> findSimilarEntities(String name, double threshold) {
        return entityRepository.findSimilarNames(name, threshold);
    }

    public List<GraphRelation> findSimilarRelations(String name, double threshold) {
        return relationRepository.findSimilarNames(name, threshold);
    }

    public TripleDTO toDTO(Triple triple) {
        GraphEntity subject = entityRepository.findById(triple.getSubjectId()).orElse(null);
        GraphEntity object = entityRepository.findById(triple.getObjectId()).orElse(null);
        GraphRelation relation = relationRepository.findById(triple.getRelationId()).orElse(null);

        return TripleDTO.builder()
                .id(triple.getId())
                .subject(subject != null ? subject.getName() : null)
                .subjectType(subject != null ? subject.getEntityType() : null)
                .relation(relation != null ? relation.getName() : null)
                .relationCategory(relation != null ? relation.getCategory() : null)
                .object(object != null ? object.getName() : null)
                .objectType(object != null ? object.getEntityType() : null)
                .timePoint(triple.getTimePoint() != null ? triple.getTimePoint().toString() : null)
                .timeStart(triple.getTimeStart() != null ? triple.getTimeStart().toString() : null)
                .timeEnd(triple.getTimeEnd() != null ? triple.getTimeEnd().toString() : null)
                .confidence(triple.getConfidence())
                .source(triple.getSource())
                .subjectAttributes(subject != null ? subject.getAttributes() : null)
                .objectAttributes(object != null ? object.getAttributes() : null)
                .build();
    }

    public void clearCache() {
        entityCache.clear();
        relationCache.clear();
    }

    public long getTotalTripleCount() {
        return tripleRepository.countTotal();
    }

    public long getTotalEntityCount() {
        return entityRepository.count();
    }

    public long getTotalRelationCount() {
        return relationRepository.count();
    }

    public GraphEntity getEntityById(Long id) {
        return entityRepository.findById(id).orElse(null);
    }

    public GraphRelation getRelationById(Long id) {
        return relationRepository.findById(id).orElse(null);
    }
}
