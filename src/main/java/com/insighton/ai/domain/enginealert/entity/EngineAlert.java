package com.insighton.ai.domain.enginealert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "engine_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engine_alert_id")
    private Long engineAlertId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_value")
    private Map<String, Object> triggerValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public EngineAlert(Long groupId, Long locationId, Long flowId,
                       String title, String message, Severity severity, Map<String, Object> triggerValue) {
        this.groupId = groupId;
        this.locationId = locationId;
        this.flowId = flowId;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.triggerValue = triggerValue;
    }
}