package com.temporalkg.service;

import com.temporalkg.dto.EvaluationReportDTO;
import com.temporalkg.entity.ModelJob;
import com.temporalkg.repository.ModelJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final ModelJobRepository modelJobRepository;

    public EvaluationReportDTO getEvaluationReport(Long modelJobId) {
        ModelJob job = modelJobRepository.findById(modelJobId).orElse(null);
        if (job == null || job.getResult() == null) return null;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(job.getResult(), EvaluationReportDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse evaluation result for job {}", modelJobId, e);
            return null;
        }
    }

    public List<EvaluationReportDTO> listEvaluations() {
        List<ModelJob> evalJobs = modelJobRepository.findAll().stream()
                .filter(j -> "EVALUATE".equals(j.getJobType()) && "COMPLETED".equals(j.getStatus()))
                .toList();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return evalJobs.stream()
                .map(j -> {
                    try {
                        return mapper.readValue(j.getResult(), EvaluationReportDTO.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }
}
