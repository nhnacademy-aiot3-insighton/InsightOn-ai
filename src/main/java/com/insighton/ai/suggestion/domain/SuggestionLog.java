package com.insighton.ai.suggestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Table(name = "suggestion_logs")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuggestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_log_id")
    private Long suggestionLogId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "suggestion_text", nullable = false, columnDefinition = "TEXT")
    private String suggestionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_payload", nullable = false)
    private String actionPayload;

    // null=대기, true=수락, false=거절
    @Column(name = "is_accepted")
    private Boolean isAccepted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public SuggestionLog(Long groupId, Long locationId, String title, String suggestionText,
                         String actionPayload, Boolean isAccepted) {
        this.groupId = groupId;
        this.locationId = locationId;
        this.title = title;
        this.suggestionText = suggestionText;
        this.actionPayload = actionPayload;
        this.isAccepted = isAccepted;
    }

    public void changeAccepted(Boolean isAccepted) {
        this.isAccepted = isAccepted;
    }
}
