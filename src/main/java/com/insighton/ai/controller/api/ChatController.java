package com.insighton.ai.controller.api;

import com.insighton.ai.controller.swagger.ChatApi;
import com.insighton.ai.domain.chatbot.dto.ChatHistoryMessage;
import com.insighton.ai.domain.chatbot.dto.ChatRequest;
import com.insighton.ai.domain.chatbot.service.ChatbotService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController implements ChatApi {

    private final ChatbotService chatbotService;

    @Override
    @GetMapping
    public List<ChatHistoryMessage> getHistory(@RequestParam Long groupId,
                                               @RequestHeader("X-User-Id") Long userId) {
        return chatbotService.getHistory(groupId, userId);
    }

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    @Override
    @PostMapping
    public SseEmitter chat(@RequestParam Long groupId,
                           @RequestParam(required = false) Long locationId,
                           @RequestHeader("X-User-Id") Long userId,
                           @RequestBody ChatRequest request) {

        SseEmitter emitter = new SseEmitter(0L);

        // FlowRecommendationChatTool처럼 도구 호출 안에서 LLM을 한 번 더 부르고 Rule Engine까지
        // 여러 번 호출하는 경우, 첫 토큰이 나오기 전까지 스트림에 아무 바이트도 안 나가는 구간이
        // 수 초~수십 초 생길 수 있다. 그 사이 배포 환경의 프록시/로드밸런서가 유휴 연결로 보고
        // 끊어버리면, AI는 뒤에서 계속 처리해 부작용(flow 생성 등)까지 완료하지만 클라이언트는
        // 이미 끊긴 연결이라 아무것도 못 받는다 - SseEmitterRegistry가 알림 스트림에서 쓰던 것과
        // 같은 하트비트를 여기도 붙인다.
        Disposable heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .subscribe(tick -> {
                    try {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    } catch (IOException | IllegalStateException e) {
                        // 연결이 이미 끊긴 경우 - 아래 onError/onCompletion 콜백이 정리한다
                    }
                });

        Disposable subscription;
        try {
            // streamChat()은 locationId 소유권 검증(ForbiddenException 등)을 구독 전에
            // 동기적으로 수행한다 - 여기서 던지면 아래 onCompletion/onTimeout/onError 등록까지
            // 못 가서 위에서 이미 시작한 heartbeat가 dispose될 기회 없이 영원히 남는다.
            subscription = chatbotService.streamChat(groupId, userId, locationId, request.message())
                    .subscribe(
                            token -> {
                                try {
                                    emitter.send(token);
                                } catch (IOException e) {
                                    emitter.complete();
                                }
                            },
                            error -> completeWithSseError(emitter, error),
                            emitter::complete
                    );
        } catch (RuntimeException e) {
            // ForbiddenException 같은 건 여기서 SSE로 흡수하면 안 된다 - GlobalExceptionHandler가
            // 정상적으로 403 등을 응답하도록 그대로 다시 던지고, 하트비트만 정리한다.
            heartbeat.dispose();
            throw e;
        }

        emitter.onCompletion(() -> {
            subscription.dispose();
            heartbeat.dispose();
        });
        emitter.onTimeout(() -> {
            subscription.dispose();
            heartbeat.dispose();
        });
        emitter.onError(throwable -> {
            subscription.dispose();
            heartbeat.dispose();
        });

        return emitter;
    }

    /**
     * completeWithError로 넘기면, 이미 text/event-stream으로 커밋된 응답에 Spring이 기본 에러
     * 바디(Map)를 쓰려다 실패하는 소음만 남는다(HttpMessageNotWritableException: No converter
     * for LinkedHashMap). 대신 SSE data 이벤트로 에러 메시지를 직접 보내고 정상 종료해서, 프론트
     * relay()가 그대로 받아 사용자에게 보여줄 수 있게 한다 - 실제 원인(LLM 호출 실패 등)은 서버
     * 로그로 남긴다.
     */
    private void completeWithSseError(SseEmitter emitter, Throwable error) {
        log.error("챗봇 응답 생성 실패", error);
        try {
            emitter.send("죄송합니다, 응답을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (IOException ignored) {
            // 이미 끊긴 연결 - 보낼 곳이 없으니 무시
        }
        emitter.complete();
    }
}
