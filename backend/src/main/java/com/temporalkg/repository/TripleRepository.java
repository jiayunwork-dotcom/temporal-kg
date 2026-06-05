package com.temporalkg.repository;

import com.temporalkg.entity.Triple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TripleRepository extends JpaRepository<Triple, Long> {

    List<Triple> findBySubjectId(Long subjectId);

    List<Triple> findByObjectId(Long objectId);

    List<Triple> findByRelationId(Long relationId);

    List<Triple> findBySubjectIdAndObjectId(Long subjectId, Long objectId);

    @Query("SELECT t FROM Triple t WHERE t.subjectId = :subjectId AND t.relationId = :relationId AND t.objectId = :objectId")
    List<Triple> findExactMatch(@Param("subjectId") Long subjectId, @Param("relationId") Long relationId, @Param("objectId") Long objectId);

    @Query("SELECT t FROM Triple t WHERE t.timePoint BETWEEN :start AND :end")
    List<Triple> findByTimePointBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("SELECT t FROM Triple t WHERE t.timeStart <= :point AND t.timeEnd >= :point")
    List<Triple> findValidAtTimePoint(@Param("point") OffsetDateTime point);

    @Query("SELECT t FROM Triple t WHERE t.timePoint <= :point OR (t.timeStart <= :point AND t.timeEnd >= :point)")
    List<Triple> findSnapshotAtTime(@Param("point") OffsetDateTime point);

    @Query("SELECT t FROM Triple t WHERE t.subjectId = :entityId OR t.objectId = :entityId ORDER BY COALESCE(t.timePoint, t.timeStart)")
    List<Triple> findEntityTimeline(@Param("entityId") Long entityId);

    @Query("SELECT t FROM Triple t WHERE t.subjectId IN :entityIds OR t.objectId IN :entityIds")
    List<Triple> findByEntityIds(@Param("entityIds") List<Long> entityIds);

    @Query(value = "SELECT DISTINCT t.* FROM triples t " +
            "WHERE (t.subject_id IN (SELECT e.id FROM entities e WHERE e.name % :query) " +
            "OR t.object_id IN (SELECT e.id FROM entities e WHERE e.name % :query)) " +
            "ORDER BY t.time_point DESC LIMIT :limit", nativeQuery = true)
    List<Triple> searchByEntityName(@Param("query") String query, @Param("limit") int limit);

    @Query("SELECT t FROM Triple t WHERE t.subjectId = :entityId OR t.objectId = :entityId")
    List<Triple> findNeighbors(@Param("entityId") Long entityId);

    @Query("SELECT COUNT(t) FROM Triple t WHERE t.subjectId = :sid AND t.relationId = :rid AND t.objectId = :oid")
    long countDuplicate(@Param("sid") Long subjectId, @Param("rid") Long relationId, @Param("oid") Long objectId);

    @Query("SELECT t FROM Triple t ORDER BY t.timePoint ASC")
    List<Triple> findAllOrderByTime();

    @Query("SELECT COUNT(t) FROM Triple t")
    long countTotal();

    @Query("SELECT COUNT(t) FROM Triple t WHERE t.subjectId = :entityId OR t.objectId = :entityId")
    int countDegree(@Param("entityId") Long entityId);

    @Modifying
    @Query("UPDATE Triple t SET t.subjectId = :newId WHERE t.subjectId = :oldId")
    void updateSubjectId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Modifying
    @Query("UPDATE Triple t SET t.objectId = :newId WHERE t.objectId = :oldId")
    void updateObjectId(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Query("SELECT MIN(COALESCE(t.timePoint, t.timeStart)) FROM Triple t")
    OffsetDateTime findEarliestTimePoint();

    @Query("SELECT MAX(COALESCE(t.timePoint, t.timeEnd)) FROM Triple t")
    OffsetDateTime findLatestTimePoint();

    @Query("SELECT FUNCTION('DATE_TRUNC', 'month', COALESCE(t.timePoint, t.timeStart)) as month, COUNT(t) as count " +
           "FROM Triple t " +
           "WHERE COALESCE(t.timePoint, t.timeStart) IS NOT NULL " +
           "GROUP BY FUNCTION('DATE_TRUNC', 'month', COALESCE(t.timePoint, t.timeStart)) " +
           "ORDER BY month")
    List<Object[]> countByMonth();
}
