package com.temporalkg.repository;

import com.temporalkg.entity.GraphEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphEntityRepository extends JpaRepository<GraphEntity, Long> {

    Optional<GraphEntity> findByNameAndEntityType(String name, String entityType);

    Optional<GraphEntity> findByName(String name);

    List<GraphEntity> findAllByName(String name);

    List<GraphEntity> findByEntityType(String entityType);

    @Query(value = "SELECT * FROM entities WHERE name % :query ORDER BY similarity(name, :query) DESC LIMIT :limit", nativeQuery = true)
    List<GraphEntity> fuzzySearchByName(@Param("query") String query, @Param("limit") int limit);

    @Query(value = "SELECT * FROM entities WHERE similarity(name, :query) > :threshold ORDER BY similarity(name, :query) DESC", nativeQuery = true)
    List<GraphEntity> findSimilarNames(@Param("query") String query, @Param("threshold") double threshold);

    @Query("SELECT e FROM GraphEntity e WHERE e.name IN :names")
    List<GraphEntity> findByNames(@Param("names") List<String> names);

    @Query(value = "SELECT COUNT(DISTINCT t.id) FROM triples t WHERE t.subject_id = :entityId OR t.object_id = :entityId", nativeQuery = true)
    int countDegree(@Param("entityId") Long entityId);
}
