package com.insighton.ai.enginealert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "trigger_value", precision = 10, scale = 2)
    private BigDecimal triggerValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public EngineAlert(Long groupId, Long locationId, Long flowId,
                       String title, String message, Severity severity, BigDecimal triggerValue) {
        this.groupId = groupId;
        this.locationId = locationId;
        this.flowId = flowId;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.triggerValue = triggerValue;
    }
}