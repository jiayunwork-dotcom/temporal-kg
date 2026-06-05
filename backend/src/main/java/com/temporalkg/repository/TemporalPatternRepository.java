package com.temporalkg.repository;

import com.temporalkg.entity.TemporalPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemporalPatternRepository extends JpaRepository<TemporalPattern, Long> {

    List<TemporalPattern> findByConfidenceGreaterThanEqualOrderByConfidenceDesc(Double minConfidence);

    List<TemporalPattern> findBySupportGreaterThanEqualOrderByConfidenceDesc(Double minSupport);

    @Query("SELECT p FROM TemporalPattern p WHERE p.support >= :minSupport AND p.confidence >= :minConfidence ORDER BY p.confidence DESC")
    List<TemporalPattern> findBySupportAndConfidence(Double minSupport, Double minConfidence);

    List<TemporalPattern> findAllByOrderByConfidenceDesc();
}
