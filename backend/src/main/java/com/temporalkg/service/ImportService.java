package com.temporalkg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temporalkg.dto.EventRecordDTO;
import com.temporalkg.entity.GraphEntity;
import com.temporalkg.entity.ImportJob;
import com.temporalkg.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final GraphService graphService;
    private final ImportJobRepository importJobRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.import.batch-size:5000}")
    private int batchSize;

    @Value("${app.import.similarity-threshold:0.85}")
    private double similarityThreshold;

    public ImportJob startImportJob(String format) {
        ImportJob job = ImportJob.builder()
                .format(format)
                .status("RUNNING")
                .build();
        return importJobRepository.save(job);
    }

    public ImportJob importCsv(MultipartFile file) {
        ImportJob job = startImportJob("CSV");
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        int[] counters = {0, 0, 0, 0};

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withTrim()
                     .withIgnoreEmptyLines()
                     .parse(reader)) {

            List<EventRecordDTO> batch = new ArrayList<>(batchSize);

            for (CSVRecord record : csvParser) {
                try {
                    EventRecordDTO event = parseCsvRecord(record);
                    if (event != null) {
                        checkSimilarEntities(event);
                        batch.add(event);
                        counters[0]++;

                        if (batch.size() >= batchSize) {
                            processBatch(batch, counters);
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    counters[2]++;
                    errors.add(String.format("Row %d: %s", record.getRecordNumber(), e.getMessage()));
                    if (errors.size() > 1000) errors.add("... too many errors, truncating");
                }
            }

            if (!batch.isEmpty()) {
                processBatch(batch, counters);
            }

            job.setTotalRecords(counters[0] + counters[2]);
            job.setProcessedRecords(counters[1]);
            job.setFailedRecords(counters[2]);
            job.setDuplicatesSkipped(counters[3]);
            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.OffsetDateTime.now());

        } catch (Exception e) {
            log.error("CSV import failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(java.time.OffsetDateTime.now());
        }

        try {
            job.setErrorDetails(objectMapper.writeValueAsString(errors));
        } catch (Exception ignored) {}
        return importJobRepository.save(job);
    }

    public ImportJob importJson(MultipartFile file) {
        ImportJob job = startImportJob("JSON");
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        int[] counters = {0, 0, 0, 0};

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<EventRecordDTO> events = parseJsonContent(content);
            counters[0] = events.size();

            List<EventRecordDTO> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < events.size(); i++) {
                try {
                    EventRecordDTO event = events.get(i);
                    checkSimilarEntities(event);
                    batch.add(event);

                    if (batch.size() >= batchSize) {
                        processBatch(batch, counters);
                        batch.clear();
                    }
                } catch (Exception e) {
                    counters[2]++;
                    errors.add(String.format("Index %d: %s", i, e.getMessage()));
                }
            }

            if (!batch.isEmpty()) {
                processBatch(batch, counters);
            }

            job.setProcessedRecords(counters[1]);
            job.setFailedRecords(counters[2]);
            job.setDuplicatesSkipped(counters[3]);
            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.OffsetDateTime.now());

        } catch (Exception e) {
            log.error("JSON import failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(java.time.OffsetDateTime.now());
        }

        try {
            job.setErrorDetails(objectMapper.writeValueAsString(errors));
        } catch (Exception ignored) {}
        return importJobRepository.save(job);
    }

    public ImportJob importEvents(List<EventRecordDTO> events) {
        ImportJob job = startImportJob("API");
        int[] counters = {events.size(), 0, 0, 0};
        List<String> errors = new ArrayList<>();

        List<EventRecordDTO> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < events.size(); i++) {
            try {
                EventRecordDTO event = events.get(i);
                checkSimilarEntities(event);
                batch.add(event);
                if (batch.size() >= batchSize) {
                    processBatch(batch, counters);
                    batch.clear();
                }
            } catch (Exception e) {
                counters[2]++;
                errors.add(String.format("Index %d: %s", i, e.getMessage()));
            }
        }
        if (!batch.isEmpty()) processBatch(batch, counters);

        job.setProcessedRecords(counters[1]);
        job.setFailedRecords(counters[2]);
        job.setDuplicatesSkipped(counters[3]);
        job.setStatus("COMPLETED");
        job.setCompletedAt(java.time.OffsetDateTime.now());

        try {
            job.setErrorDetails(objectMapper.writeValueAsString(errors));
        } catch (Exception ignored) {}
        return importJobRepository.save(job);
    }

    private void processBatch(List<EventRecordDTO> batch, int[] counters) {
        for (EventRecordDTO event : batch) {
            try {
                var triple = graphService.addTriple(event);
                if (triple != null) {
                    counters[1]++;
                } else {
                    counters[3]++;
                }
            } catch (Exception e) {
                counters[2]++;
            }
        }
    }

    private void checkSimilarEntities(EventRecordDTO event) {
        List<GraphEntity> similarSubjects = graphService.findSimilarEntities(event.getSubject(), similarityThreshold);
        similarSubjects.removeIf(e -> e.getName().equals(event.getSubject()));
        if (!similarSubjects.isEmpty()) {
            log.info("Similar entity found for '{}': {}", event.getSubject(),
                    similarSubjects.stream().map(GraphEntity::getName).toList());
        }

        List<GraphEntity> similarObjects = graphService.findSimilarEntities(event.getObject(), similarityThreshold);
        similarObjects.removeIf(e -> e.getName().equals(event.getObject()));
        if (!similarObjects.isEmpty()) {
            log.info("Similar entity found for '{}': {}", event.getObject(),
                    similarObjects.stream().map(GraphEntity::getName).toList());
        }
    }

    private EventRecordDTO parseCsvRecord(CSVRecord record) {
        String subject = record.get("subject");
        String relation = record.get("relation");
        String object = record.get("object");
        String timestamp = record.get("timestamp");

        if (subject == null || relation == null || object == null || timestamp == null) {
            return null;
        }
        if (subject.isBlank() || relation.isBlank() || object.isBlank() || timestamp.isBlank()) {
            return null;
        }

        EventRecordDTO.EventRecordDTOBuilder builder = EventRecordDTO.builder()
                .subject(subject.trim())
                .relation(relation.trim())
                .object(object.trim())
                .timestamp(timestamp.trim());

        if (record.isMapped("confidence")) {
            String conf = record.get("confidence");
            if (conf != null && !conf.isBlank()) {
                builder.confidence(Double.parseDouble(conf.trim()));
            }
        }
        if (record.isMapped("source")) {
            String src = record.get("source");
            if (src != null && !src.isBlank()) builder.source(src.trim());
        }
        if (record.isMapped("subject_type")) {
            String t = record.get("subject_type");
            if (t != null && !t.isBlank()) builder.subjectType(t.trim());
        }
        if (record.isMapped("object_type")) {
            String t = record.get("object_type");
            if (t != null && !t.isBlank()) builder.objectType(t.trim());
        }
        if (record.isMapped("relation_category")) {
            String t = record.get("relation_category");
            if (t != null && !t.isBlank()) builder.relationCategory(t.trim());
        }
        if (record.isMapped("time_end")) {
            String t = record.get("time_end");
            if (t != null && !t.isBlank()) builder.timeEnd(t.trim());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<EventRecordDTO> parseJsonContent(String content) throws Exception {
        List<EventRecordDTO> result = new ArrayList<>();
        Object parsed = objectMapper.readValue(content, Object.class);

        if (parsed instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) parsed;
            for (Map<String, Object> item : list) {
                result.add(mapToEventRecord(item));
            }
        } else if (parsed instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) parsed;
            if (map.containsKey("events")) {
                List<Map<String, Object>> events = (List<Map<String, Object>>) map.get("events");
                for (Map<String, Object> item : events) {
                    result.add(mapToEventRecord(item));
                }
            } else if (map.containsKey("subjects")) {
                List<Map<String, Object>> subjects = (List<Map<String, Object>>) map.get("subjects");
                for (Map<String, Object> subjectMap : subjects) {
                    String subjectName = (String) subjectMap.get("name");
                    String subjectType = (String) subjectMap.getOrDefault("type", "UNKNOWN");
                    List<Map<String, Object>> relations = (List<Map<String, Object>>) subjectMap.get("relations");
                    if (relations != null) {
                        for (Map<String, Object> rel : relations) {
                            result.add(mapToEventRecord(subjectName, subjectType, rel));
                        }
                    }
                }
            } else {
                result.add(mapToEventRecord(map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private EventRecordDTO mapToEventRecord(Map<String, Object> map) {
        return EventRecordDTO.builder()
                .subject(getString(map, "subject"))
                .relation(getString(map, "relation"))
                .object(getString(map, "object"))
                .timestamp(getString(map, "timestamp"))
                .confidence(getDouble(map, "confidence"))
                .source(getString(map, "source"))
                .subjectType(getString(map, "subject_type"))
                .objectType(getString(map, "object_type"))
                .relationCategory(getString(map, "relation_category"))
                .timeEnd(getString(map, "time_end"))
                .subjectAttributes(getMap(map, "subject_attributes"))
                .objectAttributes(getMap(map, "object_attributes"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private EventRecordDTO mapToEventRecord(String subjectName, String subjectType, Map<String, Object> rel) {
        return EventRecordDTO.builder()
                .subject(subjectName)
                .subjectType(subjectType)
                .relation(getString(rel, "relation"))
                .object(getString(rel, "object"))
                .objectType(getString(rel, "object_type"))
                .timestamp(getString(rel, "timestamp"))
                .timeEnd(getString(rel, "time_end"))
                .confidence(getDouble(rel, "confidence"))
                .source(getString(rel, "source"))
                .relationCategory(getString(rel, "relation_category"))
                .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }

    public ImportJob getImportJob(Long id) {
        return importJobRepository.findById(id).orElse(null);
    }

    public List<ImportJob> listImportJobs() {
        return importJobRepository.findAll();
    }
}
