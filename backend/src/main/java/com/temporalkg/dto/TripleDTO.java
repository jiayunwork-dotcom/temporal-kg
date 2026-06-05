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
public class TripleDTO {
    private Long id;
    private String subject;
    private String subjectType;
    private String relation;
    private String relationCategory;
    private String object;
    private String objectType;
    private String timePoint;
    private String timeStart;
    private String timeEnd;
    private Double confidence;
    private String source;
    private Map<String, Object> subjectAttributes;
    private Map<String, Object> objectAttributes;
}
