package com.temporalkg.service;

import com.temporalkg.repository.GraphEntityRepository;
import com.temporalkg.repository.GraphRelationRepository;
import com.temporalkg.repository.TripleRepository;
import com.temporalkg.repository.AlertRepository;
import com.temporalkg.repository.TemporalPatternRepository;
import com.temporalkg.repository.ModelJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final GraphEntityRepository entityRepository;
    private final GraphRelationRepository relationRepository;
    private final TripleRepository tripleRepository;
    private final AlertRepository alertRepository;
    private final TemporalPatternRepository patternRepository;
    private final ModelJobRepository modelJobRepository;

    public Map<String, Object> getOverview() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEntities", entityRepository.count());
        stats.put("totalRelations", relationRepository.count());
        stats.put("totalTriples", tripleRepository.countTotal());
        stats.put("totalPatterns", patternRepository.count());
        stats.put("activeAlerts", alertRepository.findByStatusOrderByCreatedAtDesc("ACTIVE").size());
        stats.put("pendingJobs", modelJobRepository.findByStatusOrderByCreatedAtDesc("PENDING").size());
        stats.put("runningJobs", modelJobRepository.findByStatusOrderByCreatedAtDesc("RUNNING").size());

        Map<String, Long> entityTypeDist = new LinkedHashMap<>();
        entityRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getEntityType(), java.util.stream.Collectors.counting()))
                .forEach(entityTypeDist::put);
        stats.put("entityTypeDistribution", entityTypeDist);

        return stats;
    }
}
