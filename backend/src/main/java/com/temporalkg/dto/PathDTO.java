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
public class PathDTO {
    private List<GraphNodeDTO> nodes;
    private List<GraphEdgeDTO> edges;
    private Integer length;
    private Map<String, Object> metadata;
}
