package com.temporalkg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temporalkg.dto.PredictionRequestDTO;
import com.temporalkg.dto.PredictionResultDTO;
import com.temporalkg.entity.ModelJob;
import com.temporalkg.repository.ModelJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlService {

    private final StringRedisTemplate redisTemplate;
    private final ModelJobRepository modelJobRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.model.task-queue}")
    private String taskQueue;

    @Value("${app.model.result-queue}")
    private String resultQueue;

    @Value("${app.model.poll-timeout-ms}")
    private long pollTimeoutMs;

    public ModelJob submitTrainingJob(String modelType, Map<String, Object> params) {
        ModelJob job = ModelJob.builder()
                .jobType("TRAIN")
                .modelType(modelType)
                .status("PENDING")
                .params(params != null ? params.toString() : "{}")
                .build();
        job = modelJobRepository.save(job);

        try {
            Map<String, Object> task = new HashMap<>();
            task.put("jobId", job.getId());
            task.put("type", "TRAIN");
            task.put("modelType", modelType);
            task.put("params", params);
            redisTemplate.convertAndSend(taskQueue, objectMapper.writeValueAsString(task));
            log.info("Submitted training job {} for model {}", job.getId(), modelType);
        } catch (Exception e) {
            log.error("Failed to submit training job", e);
            job.setStatus("FAILED");
            job.setErrorMessage("Failed to submit: " + e.getMessage());
            modelJobRepository.save(job);
        }
        return job;
    }

    public PredictionResultDTO predict(PredictionRequestDTO request) {
        String correlationId = UUID.randomUUID().toString();
        String resultKey = "tkg:prediction:" + correlationId;

        try {
            Map<String, Object> task = new HashMap<>();
            task.put("type", "PREDICT");
            task.put("correlationId", correlationId);
            task.put("modelType", request.getModelType());
            task.put("subject", request.getSubject());
            task.put("relation", request.getRelation());
            task.put("object", request.getObject());
            task.put("timestamp", request.getTimestamp());
            task.put("topK", request.getTopK() != null ? request.getTopK() : 10);
            task.put("params", request.getParams());

            redisTemplate.convertAndSend(taskQueue, objectMapper.writeValueAsString(task));

            String result = redisTemplate.opsForValue().getAndDelete(resultKey);
            long startTime = System.currentTimeMillis();
            while (result == null && (System.currentTimeMillis() - startTime) < pollTimeoutMs) {
                Thread.sleep(500);
                result = redisTemplate.opsForValue().getAndDelete(resultKey);
            }

            if (result != null) {
                return objectMapper.readValue(result, PredictionResultDTO.class);
            } else {
                return PredictionResultDTO.builder()
                        .predictions(java.util.List.of())
                        .modelType(request.getModelType())
                        .metadata(Map.of("error", "Prediction timed out"))
                        .build();
            }
        } catch (Exception e) {
            log.error("Prediction failed", e);
            return PredictionResultDTO.builder()
                    .predictions(java.util.List.of())
                    .modelType(request.getModelType())
                    .metadata(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    public ModelJob submitEvaluationJob(String modelType, Long modelJobId, Map<String, Object> params) {
        ModelJob job = ModelJob.builder()
                .jobType("EVALUATE")
                .modelType(modelType)
                .status("PENDING")
                .params(params != null ? params.toString() : "{}")
                .build();
        job = modelJobRepository.save(job);

        try {
            Map<String, Object> task = new HashMap<>();
            task.put("jobId", job.getId());
            task.put("type", "EVALUATE");
            task.put("modelType", modelType);
            task.put("params", params);
            redisTemplate.convertAndSend(taskQueue, objectMapper.writeValueAsString(task));
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            modelJobRepository.save(job);
        }
        return job;
    }

    public ModelJob submitMiningJob(Map<String, Object> params) {
        ModelJob job = ModelJob.builder()
                .jobType("MINE_PATTERNS")
                .modelType("PATTERN_MINER")
                .status("PENDING")
                .params(params != null ? params.toString() : "{}")
                .build();
        job = modelJobRepository.save(job);

        try {
            Map<String, Object> task = new HashMap<>();
            task.put("jobId", job.getId());
            task.put("type", "MINE_PATTERNS");
            task.put("params", params);
            redisTemplate.convertAndSend(taskQueue, objectMapper.writeValueAsString(task));
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            modelJobRepository.save(job);
        }
        return job;
    }

    public ModelJob getJobStatus(Long jobId) {
        return modelJobRepository.findById(jobId).orElse(null);
    }

    public java.util.List<ModelJob> listJobs() {
        return modelJobRepository.findAll();
    }

    public void updateJobResult(Long jobId, String result) {
        ModelJob job = modelJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus("COMPLETED");
            job.setResult(result);
            job.setCompletedAt(java.time.OffsetDateTime.now());
            modelJobRepository.save(job);
        }
    }

    public void updateJobError(Long jobId, String error) {
        ModelJob job = modelJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus("FAILED");
            job.setErrorMessage(error);
            job.setCompletedAt(java.time.OffsetDateTime.now());
            modelJobRepository.save(job);
        }
    }
}
