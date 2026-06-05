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
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pattern_id")
    private Long patternId;

    @Column(name = "trigger_triple_id")
    private Long triggerTripleId;

    @Column(name = "predicted_event", nullable = false, columnDefinition = "jsonb")
    private String predictedEvent;

    @Column(name = "predicted_time_window", length = 128)
    private String predictedTimeWindow;

    private Double confidence;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
