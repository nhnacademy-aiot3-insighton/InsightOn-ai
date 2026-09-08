package com.insighton.ai.domain.suggestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "AI 제안 로그 생성 요청 (내부용, 외부 API로는 노출 안 됨)")
public record SuggestionLogCreateRequest(
        @Schema(description = "그룹 ID", example = "5")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        @NotNull
        Long locationId,

        @Schema(description = "제목")
        @NotBlank
        String title,

        @Schema(description = "제안 문구")
        @NotBlank
        String suggestionText,

        @Schema(description = "수락 시 Core 제어 API에 넘길 인자 (JSON 문자열)")
        @NotBlank
        String actionPayload,

        @Schema(description = "생성 시 수락 여부 — SUGGESTION 모드는 null(대기), AI_DIRECT 모드는 true로 넘김")
        Boolean isAccepted
) {
}
