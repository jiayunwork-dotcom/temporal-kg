package com.temporalkg.controller;

import com.temporalkg.dto.*;
import com.temporalkg.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final QueryService queryService;

    @GetMapping("/query")
    public ResponseEntity<List<TripleDTO>> basicQuery(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String relation,
            @RequestParam(required = false) String timeStart,
            @RequestParam(required = false) String timeEnd) {
        return ResponseEntity.ok(queryService.basicQuery(entity, relation, timeStart, timeEnd));
    }

    @GetMapping("/paths")
    public ResponseEntity<List<PathDTO>> findPaths(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "5") int maxHops,
            @RequestParam(defaultValue = "true") boolean shortestOnly) {
        return ResponseEntity.ok(queryService.findPaths(from, to, maxHops, shortestOnly));
    }

    @GetMapping("/subgraph")
    public ResponseEntity<SubGraphDTO> extractSubGraph(
            @RequestParam String entity,
            @RequestParam(defaultValue = "2") int hops) {
        return ResponseEntity.ok(queryService.extractSubGraph(entity, hops));
    }

    @GetMapping("/time-slice")
    public ResponseEntity<SubGraphDTO> timeSlice(@RequestParam String timePoint) {
        return ResponseEntity.ok(queryService.timeSlice(timePoint));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TripleDTO>> entityTimeline(@RequestParam String entity) {
        return ResponseEntity.ok(queryService.entityTimeline(entity));
    }

    @PostMapping("/pattern-match")
    public ResponseEntity<List<SubGraphDTO>> patternMatch(@RequestBody String patternJson) {
        return ResponseEntity.ok(queryService.patternMatch(patternJson));
    }

    @GetMapping("/full")
    public ResponseEntity<SubGraphDTO> getFullGraph(
            @RequestParam(defaultValue = "500") int limit) {
        return ResponseEntity.ok(queryService.getFullGraph(limit));
    }

    @GetMapping("/entity-detail")
    public ResponseEntity<EntityDetailDTO> getEntityDetail(@RequestParam Long entityId) {
        EntityDetailDTO detail = queryService.getEntityDetail(entityId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }
}
