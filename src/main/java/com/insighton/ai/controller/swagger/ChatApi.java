// controller/swagger/ChatApi.java
package com.insighton.ai.controller.swagger;

import com.insighton.ai.domain.chatbot.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat", description = "AI 챗봇 대화 API")
public interface ChatApi {

    @Operation(summary = "챗봇에게 메시지 전송",
            description = "그룹 데이터를 조회할 수 있는 챗봇과 대화한다. SSE로 토큰 단위 스트리밍 응답을 반환한다.")
    @ApiResponse(responseCode = "200", description = "스트리밍 응답 시작", content = @Content(mediaType = "text/event-stream"))
    SseEmitter chat(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long groupId,
            @Parameter(description = "위치 ID (특정 위치 페이지에서 대화 시에만 전달)", example = "42",
                    schema = @Schema(type = "integer", format = "int64"))
            Long locationId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long userId,
            @Parameter(description = "사용자 메시지")
            ChatRequest request
    );
}