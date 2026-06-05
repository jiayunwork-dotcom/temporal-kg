package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubGraphDTO {
    private List<GraphNodeDTO> nodes;
    private List<GraphEdgeDTO> edges;
}
