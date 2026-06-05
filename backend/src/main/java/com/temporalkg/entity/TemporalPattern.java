package com.temporalkg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "temporal_patterns")
public class TemporalPattern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String antecedent;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String consequent;

    @Column(nullable = false)
    private Double support;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "avg_time_interval_hours")
    private Double avgTimeIntervalHours;

    @Column(name = "pattern_type", nullable = false, length = 32)
    @Builder.Default
    private String patternType = "PAIR";

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
