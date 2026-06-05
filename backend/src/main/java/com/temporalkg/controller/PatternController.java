package com.temporalkg.controller;

import com.temporalkg.entity.TemporalPattern;
import com.temporalkg.service.PatternService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patterns")
@RequiredArgsConstructor
public class PatternController {

    private final PatternService patternService;

    @GetMapping
    public ResponseEntity<List<TemporalPattern>> listPatterns(
            @RequestParam(required = false) Double minSupport,
            @RequestParam(required = false) Double minConfidence) {
        return ResponseEntity.ok(patternService.getPatternsByThreshold(minSupport, minConfidence));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemporalPattern> getPattern(@PathVariable Long id) {
        TemporalPattern pattern = patternService.getPattern(id);
        return pattern != null ? ResponseEntity.ok(pattern) : ResponseEntity.notFound().build();
    }
}
