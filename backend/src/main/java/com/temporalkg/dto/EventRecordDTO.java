package com.temporalkg.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecordDTO {
    @NotBlank(message = "subject is required")
    private String subject;

    @NotBlank(message = "relation is required")
    private String relation;

    @NotBlank(message = "object is required")
    private String object;

    @NotBlank(message = "timestamp is required")
    private String timestamp;

    private Double confidence;
    private String source;
    private String subjectType;
    private String objectType;
    private String relationCategory;
    private String timeEnd;
    private Map<String, Object> subjectAttributes;
    private Map<String, Object> objectAttributes;
}
