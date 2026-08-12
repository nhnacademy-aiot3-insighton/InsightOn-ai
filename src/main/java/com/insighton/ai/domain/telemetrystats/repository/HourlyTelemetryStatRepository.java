package com.insighton.ai.domain.telemetrystats.repository;

import com.insighton.ai.domain.telemetrystats.entity.HourlyTelemetryStat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HourlyTelemetryStatRepository extends JpaRepository<HourlyTelemetryStat, Long> {

    @Query("""
            select h from HourlyTelemetryStat h
            where h.locationId = :locationId
            and (cast(:from as java.time.OffsetDateTime) is null or h.logHour >= :from)
            and (cast(:to as java.time.OffsetDateTime) is null or h.logHour <= :to)
            order by h.logHour desc
            """)
    List<HourlyTelemetryStat> search(@Param("locationId") Long locationId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     Pageable pageable);

    @Query("""
            select count(h) from HourlyTelemetryStat h
            where h.locationId = :locationId
            and (cast(:from as java.time.OffsetDateTime) is null or h.logHour >= :from)
            and (cast(:to as java.time.OffsetDateTime) is null or h.logHour <= :to)
            """)
    long count(@Param("locationId") Long locationId,
               @Param("from") OffsetDateTime from,
               @Param("to") OffsetDateTime to);

    Optional<HourlyTelemetryStat> findByLocationIdAndLogHour(Long locationId, OffsetDateTime logHour);

    @Modifying
    @Query("delete from HourlyTelemetryStat h where h.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);

    @Modifying
    @Query("delete from HourlyTelemetryStat h where h.locationId in :locationIds")
    void deleteByLocationIdIn(@Param("locationIds") List<Long> locationIds);


    @Query("""
                   select distinct h.locationId from HourlyTelemetryStat h
                   where h.logHour >= :from
                   and h.logHour <= :to
            """)
    List<Long> findDistinctLocationIds(OffsetDateTime from, OffsetDateTime to);
}
