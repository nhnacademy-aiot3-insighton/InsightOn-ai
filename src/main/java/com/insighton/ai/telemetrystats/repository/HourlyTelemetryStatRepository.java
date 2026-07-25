package com.insighton.ai.telemetrystats.repository;

import com.insighton.ai.telemetrystats.domain.HourlyTelemetryStat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HourlyTelemetryStatRepository extends JpaRepository<HourlyTelemetryStat, Long> {

    @Query("""
            select h from HourlyTelemetryStat h
            where h.groupId = :groupId
            and (:locationId is null or h.locationId = :locationId)
            and (:from is null or h.logHour >= :from)
            and (:to is null or h.logHour <= :to)
            order by h.logHour desc
            """)
    List<HourlyTelemetryStat> search(@Param("groupId") Long groupId,
                                     @Param("locationId") Long locationId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to);

    Optional<HourlyTelemetryStat> findByLocationIdAndLogHour(Long locationId, OffsetDateTime logHour);
}
