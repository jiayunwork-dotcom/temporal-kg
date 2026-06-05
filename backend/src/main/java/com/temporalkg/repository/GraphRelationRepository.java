package com.temporalkg.repository;

import com.temporalkg.entity.GraphRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphRelationRepository extends JpaRepository<GraphRelation, Long> {

    Optional<GraphRelation> findByName(String name);

    List<GraphRelation> findByCategory(String category);

    @Query(value = "SELECT * FROM relations WHERE similarity(name, :query) > :threshold ORDER BY similarity(name, :query) DESC", nativeQuery = true)
    List<GraphRelation> findSimilarNames(@Param("query") String query, @Param("threshold") double threshold);

    @Query("SELECT r FROM GraphRelation r WHERE r.name IN :names")
    List<GraphRelation> findByNames(@Param("names") List<String> names);
}
