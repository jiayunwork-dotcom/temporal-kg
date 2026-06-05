package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationReportDTO {
    private Long jobId;
    private String modelType;
    private Double mrr;
    private Double hitsAt1;
    private Double hitsAt3;
    private Double hitsAt10;
    private Map<String, Map<String, Double>> timeAwareMetrics;
    private Map<String, Double> patternMetrics;
    private Map<String, Object> params;
}
