package com.temporalkg.repository;

import com.temporalkg.entity.ModelJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelJobRepository extends JpaRepository<ModelJob, Long> {
    List<ModelJob> findByStatusOrderByCreatedAtDesc(String status);
    List<ModelJob> findByModelTypeOrderByCreatedAtDesc(String modelType);
}
