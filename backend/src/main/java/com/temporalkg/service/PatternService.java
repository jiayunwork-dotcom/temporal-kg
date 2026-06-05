package com.temporalkg.service;

import com.temporalkg.entity.TemporalPattern;
import com.temporalkg.repository.TemporalPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatternService {

    private final TemporalPatternRepository patternRepository;

    public List<TemporalPattern> getAllPatterns() {
        return patternRepository.findAllByOrderByConfidenceDesc();
    }

    public List<TemporalPattern> getPatternsByThreshold(Double minSupport, Double minConfidence) {
        if (minSupport != null && minConfidence != null) {
            return patternRepository.findBySupportAndConfidence(minSupport, minConfidence);
        } else if (minConfidence != null) {
            return patternRepository.findByConfidenceGreaterThanEqualOrderByConfidenceDesc(minConfidence);
        } else if (minSupport != null) {
            return patternRepository.findBySupportGreaterThanEqualOrderByConfidenceDesc(minSupport);
        }
        return getAllPatterns();
    }

    public TemporalPattern getPattern(Long id) {
        return patternRepository.findById(id).orElse(null);
    }
}
