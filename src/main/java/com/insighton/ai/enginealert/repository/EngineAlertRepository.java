package com.insighton.ai.enginealert.repository;

import com.insighton.ai.enginealert.domain.EngineAlert;
import com.insighton.ai.enginealert.domain.Severity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineAlertRepository extends JpaRepository<EngineAlert, Long> {

    @Query("""
            select e from EngineAlert e
            where e.groupId = :groupId
            and (:locationId is null or e.locationId = :locationId)
            and (:severity is null or e.severity = :severity)
            order by e.createdAt desc
            """)
    List<EngineAlert> search(@Param("groupId") Long groupId, @Param("locationId") Long locationId,
                              @Param("severity") Severity severity);

    @Modifying
    @Query("delete from EngineAlert e where e.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from EngineAlert e where e.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);
}
