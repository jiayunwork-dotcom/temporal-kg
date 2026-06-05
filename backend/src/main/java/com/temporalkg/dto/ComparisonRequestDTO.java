package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonRequestDTO {
    private String t1;
    private String t2;
    private String filterEntity;
    private String filterRelation;
}
