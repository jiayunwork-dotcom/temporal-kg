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
public class PredictionRequestDTO {
    private String modelType;
    private String subject;
    private String relation;
    private String object;
    private String timestamp;
    private Integer topK;
    private Map<String, Object> params;
}
