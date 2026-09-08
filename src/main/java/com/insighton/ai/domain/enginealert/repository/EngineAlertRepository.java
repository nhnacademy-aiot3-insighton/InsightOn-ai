package com.insighton.ai.domain.enginealert.repository;

import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineAlertRepository extends JpaRepository<EngineAlert, Long>, EngineAlertQueryRepository {

    @Query(value = """
            insert into engine_alerts (event_id, group_id, location_id, flow_id, title, message, severity, trigger_value, created_at)
            values (:eventId, :groupId, :locationId, :flowId, :title, :message, :severity, cast(:triggerValue as jsonb), now())
            on conflict (event_id) do nothing
            returning engine_alert_id
            """, nativeQuery = true)
    Optional<Long> claimEventId(@Param("eventId") String eventId, @Param("groupId") Long groupId,
                                @Param("locationId") Long locationId, @Param("flowId") Long flowId,
                                @Param("title") String title, @Param("message") String message,
                                @Param("severity") String severity, @Param("triggerValue") String triggerValueJson);

    @Modifying
    @Query("delete from EngineAlert e where e.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from EngineAlert e where e.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);
}
