package com.temporalkg.controller;

import com.temporalkg.dto.EventRecordDTO;
import com.temporalkg.dto.PredictionRequestDTO;
import com.temporalkg.dto.PredictionResultDTO;
import com.temporalkg.entity.Alert;
import com.temporalkg.entity.ModelJob;
import com.temporalkg.service.IncrementalService;
import com.temporalkg.service.MlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MlController {

    private final MlService mlService;
    private final IncrementalService incrementalService;

    @PostMapping("/train")
    public ResponseEntity<ModelJob> train(
            @RequestParam String modelType,
            @RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(mlService.submitTrainingJob(modelType, params));
    }

    @PostMapping("/predict")
    public ResponseEntity<PredictionResultDTO> predict(@RequestBody PredictionRequestDTO request) {
        return ResponseEntity.ok(mlService.predict(request));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ModelJob> evaluate(
            @RequestParam String modelType,
            @RequestParam(required = false) Long modelJobId,
            @RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(mlService.submitEvaluationJob(modelType, modelJobId, params));
    }

    @PostMapping("/mine-patterns")
    public ResponseEntity<ModelJob> minePatterns(@RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(mlService.submitMiningJob(params));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ModelJob>> listJobs() {
        return ResponseEntity.ok(mlService.listJobs());
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ModelJob> getJob(@PathVariable Long id) {
        ModelJob job = mlService.getJobStatus(id);
        return job != null ? ResponseEntity.ok(job) : ResponseEntity.notFound().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestEvent(@RequestBody EventRecordDTO event) {
        var triple = incrementalService.ingestEvent(event);
        return ResponseEntity.ok(Map.of("created", triple != null, "tripleId", triple != null ? triple.getId() : null));
    }

    @PostMapping("/events/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@RequestBody List<EventRecordDTO> events) {
        var results = incrementalService.ingestBatch(events);
        return ResponseEntity.ok(Map.of("created", results.size()));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Alert>> getAlerts() {
        return ResponseEntity.ok(incrementalService.getActiveAlerts());
    }

    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<Void> dismissAlert(@PathVariable Long id) {
        incrementalService.dismissAlert(id);
        return ResponseEntity.ok().build();
    }
}
