package com.temporalkg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temporalkg.dto.EventRecordDTO;
import com.temporalkg.entity.Alert;
import com.temporalkg.entity.GraphEntity;
import com.temporalkg.entity.TemporalPattern;
import com.temporalkg.entity.Triple;
import com.temporalkg.repository.AlertRepository;
import com.temporalkg.repository.TemporalPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncrementalService {

    private final GraphService graphService;
    private final TemporalPatternRepository patternRepository;
    private final AlertRepository alertRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.model.incremental-threshold:1000}")
    private int incrementalThreshold;

    @Value("${app.model.task-queue}")
    private String taskQueue;

    public Triple ingestEvent(EventRecordDTO event) {
        Triple triple = graphService.addTriple(event);
        if (triple != null) {
            checkPatternMatch(triple);
            checkIncrementalRetrain();
        }
        return triple;
    }

    public List<Triple> ingestBatch(List<EventRecordDTO> events) {
        List<Triple> results = new java.util.ArrayList<>();
        for (EventRecordDTO event : events) {
            Triple t = graphService.addTriple(event);
            if (t != null) {
                results.add(t);
                checkPatternMatch(t);
            }
        }
        checkIncrementalRetrain();
        return results;
    }

    private void checkPatternMatch(Triple newTriple) {
        List<TemporalPattern> patterns = patternRepository.findAllByOrderByConfidenceDesc();
        GraphEntity subject = graphService.getEntityById(newTriple.getSubjectId());
        GraphEntity object = graphService.getEntityById(newTriple.getObjectId());

        for (TemporalPattern pattern : patterns) {
            if (pattern.getConfidence() < 0.5) continue;

            try {
                Map<String, Object> antecedent = objectMapper.readValue(pattern.getAntecedent(), Map.class);
                if (matchesAntecedent(antecedent, subject, newTriple)) {
                    Map<String, Object> consequent = objectMapper.readValue(pattern.getConsequent(), Map.class);
                    String timeWindow = pattern.getAvgTimeIntervalHours() != null
                            ? String.format("%.0f小时内", pattern.getAvgTimeIntervalHours())
                            : "未知时间窗口";

                    Alert alert = Alert.builder()
                            .patternId(pattern.getId())
                            .triggerTripleId(newTriple.getId())
                            .predictedEvent(objectMapper.writeValueAsString(consequent))
                            .predictedTimeWindow(timeWindow)
                            .confidence(pattern.getConfidence())
                            .build();
                    alertRepository.save(alert);
                    log.info("Alert generated: pattern {} triggered by triple {}", pattern.getId(), newTriple.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to check pattern match for pattern {}", pattern.getId(), e);
            }
        }
    }

    private boolean matchesAntecedent(Map<String, Object> antecedent, GraphEntity entity, Triple triple) {
        String entityType = (String) antecedent.get("entity_type");
        String relationName = (String) antecedent.get("relation");

        if (entityType != null && !entityType.equals(entity.getEntityType())) return false;
        if (relationName != null) {
            var rel = graphService.getRelationById(triple.getRelationId());
            if (rel == null || !relationName.equals(rel.getName())) return false;
        }
        return true;
    }

    private void checkIncrementalRetrain() {
        String counterKey = "tkg:incremental:counter";
        Long count = redisTemplate.opsForValue().increment(counterKey);

        if (count != null && count >= incrementalThreshold) {
            redisTemplate.opsForValue().set(counterKey, "0");
            try {
                Map<String, Object> task = new HashMap<>();
                task.put("type", "INCREMENTAL_TRAIN");
                task.put("params", Map.of("epochs", 5));
                redisTemplate.convertAndSend(taskQueue, objectMapper.writeValueAsString(task));
                log.info("Triggered incremental retraining after {} new events", incrementalThreshold);
            } catch (Exception e) {
                log.error("Failed to trigger incremental retraining", e);
            }
        }
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }

    public void dismissAlert(Long alertId) {
        alertRepository.findById(alertId).ifPresent(alert -> {
            alert.setStatus("DISMISSED");
            alertRepository.save(alert);
        });
    }
}
