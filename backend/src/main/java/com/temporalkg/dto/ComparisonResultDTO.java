package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResultDTO {

    private DiffSummary diffSummary;
    private EvolutionMetrics evolutionMetrics;
    private List<DiffTripleDTO> addedTriples;
    private List<DiffTripleDTO> deletedTriples;
    private List<DiffTripleDTO> persistedTriples;
    private List<DiffTripleDTO> changedTriples;
    private List<DiffNodeDTO> nodes;
    private List<EntityActivityDTO> entityActivities;
    private TransferMatrixDTO transferMatrix;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffSummary {
        private int addedCount;
        private int deletedCount;
        private int persistedCount;
        private int changedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionMetrics {
        private Double densityChangeRate;
        private Double t1Density;
        private Double t2Density;
        private Double centralityDrift;
        private Double communityStability;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffTripleDTO {
        private Long id;
        private String subject;
        private String subjectType;
        private String relation;
        private String object;
        private String objectType;
        private String diffType;
        private String oldRelation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffNodeDTO {
        private Long id;
        private String name;
        private String type;
        private Double activity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityActivityDTO {
        private Long entityId;
        private String entityName;
        private int eventCount;
        private Double activityScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferMatrixDTO {
        private List<String> relationTypes;
        private List<List<Integer>> matrix;
    }
}
