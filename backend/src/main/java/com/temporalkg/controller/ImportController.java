package com.temporalkg.controller;

import com.temporalkg.dto.EventRecordDTO;
import com.temporalkg.entity.ImportJob;
import com.temporalkg.service.ImportService;
import com.temporalkg.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final GraphService graphService;

    @PostMapping("/csv")
    public ResponseEntity<ImportJob> importCsv(@RequestParam("file") MultipartFile file) {
        ImportJob job = importService.importCsv(file);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/json")
    public ResponseEntity<ImportJob> importJson(@RequestParam("file") MultipartFile file) {
        ImportJob job = importService.importJson(file);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/events")
    public ResponseEntity<ImportJob> importEvents(@RequestBody List<EventRecordDTO> events) {
        ImportJob job = importService.importEvents(events);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ImportJob>> listJobs() {
        return ResponseEntity.ok(importService.listImportJobs());
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ImportJob> getJob(@PathVariable Long id) {
        ImportJob job = importService.getImportJob(id);
        return job != null ? ResponseEntity.ok(job) : ResponseEntity.notFound().build();
    }

    @GetMapping("/similar-entities")
    public ResponseEntity<List<Map<String, Object>>> findSimilarEntities(
            @RequestParam String name,
            @RequestParam(defaultValue = "0.7") double threshold) {
        List<com.temporalkg.entity.GraphEntity> similar = graphService.findSimilarEntities(name, threshold);
        List<Map<String, Object>> result = similar.stream()
                .map(e -> Map.<String, Object>of("id", e.getId(), "name", e.getName(), "type", e.getEntityType()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/similar-relations")
    public ResponseEntity<List<Map<String, Object>>> findSimilarRelations(
            @RequestParam String name,
            @RequestParam(defaultValue = "0.7") double threshold) {
        List<com.temporalkg.entity.GraphRelation> similar = graphService.findSimilarRelations(name, threshold);
        List<Map<String, Object>> result = similar.stream()
                .map(r -> Map.<String, Object>of("id", r.getId(), "name", r.getName(), "category", r.getCategory()))
                .toList();
        return ResponseEntity.ok(result);
    }
}
