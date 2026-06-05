package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDTO {
    private Long id;
    private Long source;
    private Long target;
    private String relation;
    private String relationCategory;
    private String timePoint;
    private String timeStart;
    private String timeEnd;
    private Double confidence;
    private Double weight;
}
