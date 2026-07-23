package com.insighton.ai.suggestion.dto;

import com.insighton.ai.suggestion.domain.SuggestionLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "AI 제안 로그")
public record SuggestionLogResponse(
        @Schema(description = "제안 로그 ID", example = "1") Long suggestionLogId,
        @Schema(description = "그룹 ID", example = "5") Long groupId,
        @Schema(description = "위치 ID", example = "42") Long locationId,
        @Schema(description = "제목") String title,
        @Schema(description = "제안 문구") String suggestionText,
        @Schema(description = "수락 여부 (null=대기, true=수락, false=거절)") Boolean isAccepted,
        @Schema(description = "수락 시 Core 제어 API에 넘길 인자 (JSON 문자열)") String actionPayload,
        @Schema(description = "생성일시") OffsetDateTime createdAt
) {
    public static SuggestionLogResponse from(SuggestionLog log) {
        return new SuggestionLogResponse(
                log.getSuggestionLogId(),
                log.getGroupId(),
                log.getLocationId(),
                log.getTitle(),
                log.getSuggestionText(),
                log.getIsAccepted(),
                log.getActionPayload(),
                log.getCreatedAt()
        );
    }
}