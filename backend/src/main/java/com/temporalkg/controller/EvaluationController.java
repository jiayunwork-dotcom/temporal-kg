package com.temporalkg.controller;

import com.temporalkg.dto.EvaluationReportDTO;
import com.temporalkg.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/report/{jobId}")
    public ResponseEntity<EvaluationReportDTO> getReport(@PathVariable Long jobId) {
        EvaluationReportDTO report = evaluationService.getEvaluationReport(jobId);
        return report != null ? ResponseEntity.ok(report) : ResponseEntity.notFound().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<List<EvaluationReportDTO>> listReports() {
        return ResponseEntity.ok(evaluationService.listEvaluations());
    }
}
