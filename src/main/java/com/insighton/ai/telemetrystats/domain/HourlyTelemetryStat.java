package com.insighton.ai.telemetrystats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "hourly_telemetry_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"location_id", "log_hour"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HourlyTelemetryStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hourly_telemetry_stat_id")
    private Long hourlyTelemetryStatId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    // 정각 배치가 집계한 시간대 (예: 2026-07-23T14:00:00+09:00)
    @Column(name = "log_hour", nullable = false)
    private OffsetDateTime logHour;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_avg", nullable = false)
    private String metricsAvg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_max", nullable = false)
    private String metricsMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_min", nullable = false)
    private String metricsMin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actuator_on_minutes", nullable = false)
    private String actuatorOnMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public HourlyTelemetryStat(Long groupId, Long locationId, OffsetDateTime logHour,
                               String metricsAvg, String metricsMax, String metricsMin,
                               String actuatorOnMinutes) {
        this.groupId = groupId;
        this.locationId = locationId;
        this.logHour = logHour;
        this.metricsAvg = metricsAvg;
        this.metricsMax = metricsMax;
        this.metricsMin = metricsMin;
        this.actuatorOnMinutes = actuatorOnMinutes;
    }
}
