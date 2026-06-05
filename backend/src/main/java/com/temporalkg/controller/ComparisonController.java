package com.temporalkg.controller;

import com.temporalkg.dto.ComparisonRequestDTO;
import com.temporalkg.dto.ComparisonResultDTO;
import com.temporalkg.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comparison")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @PostMapping
    public ResponseEntity<ComparisonResultDTO> compare(@RequestBody ComparisonRequestDTO request) {
        ComparisonResultDTO result = comparisonService.compare(request);
        return ResponseEntity.ok(result);
    }
}
