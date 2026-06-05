package com.temporalkg.controller;

import com.temporalkg.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(statsService.getOverview());
    }

    @GetMapping("/time-range")
    public ResponseEntity<Map<String, Object>> getTimeRange() {
        return ResponseEntity.ok(statsService.getTimeRange());
    }
}
