package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeDTO {
    private Long id;
    private String name;
    private String type;
    private Map<String, Object> attributes;
    private Integer degree;

    public GraphNodeDTO(Long id, String name, String type, Integer degree) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.degree = degree;
    }
}
