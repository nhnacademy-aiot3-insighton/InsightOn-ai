package com.insighton.ai.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "404") int status,
        @Schema(description = "에러 메시지") String message,
        @Schema(description = "발생 시각") OffsetDateTime timestamp
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, OffsetDateTime.now());
    }
}
