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
@Table(name = "triples")
public class Triple {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "relation_id", nullable = false)
    private Long relationId;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "time_point")
    private OffsetDateTime timePoint;

    @Column(name = "time_start")
    private OffsetDateTime timeStart;

    @Column(name = "time_end")
    private OffsetDateTime timeEnd;

    @Builder.Default
    private Double confidence = 1.0;

    @Column(length = 512)
    private String source;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
