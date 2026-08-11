package com.insighton.ai.domain.chatbot.service;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final String LOCK_KEY_PREFIX = "chat-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_RETRY_DELAY = Duration.ofMillis(200);
    private static final long LOCK_MAX_RETRIES = 50;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 InsightOn IoT 관제 플랫폼의 챗봇입니다. 오늘은 %s입니다.
            사용자의 그룹 데이터(리포트, 알람, AI 제안, 알림, 센서 통계, 위치 정보)를
            도구를 호출해서 조회하고 친절하게 설명해주세요.
            
            기간 표현("한달 전", "1월부터 6월까지", "저번주" 등)이 나오면 오늘 날짜를 기준으로
            구체적인 시작/종료 시각을 계산해서 도구 호출에 사용하세요.
            리포트/알람/제안을 ID로 조회하려면 먼저 목록 조회 도구로 제목/생성일을 확인한 뒤,
            일치하는 항목의 ID로 상세 조회 도구를 호출하세요.
            
            도메인 지식:
            - 위치의 자동제어 모드는 SUGGESTION(AI가 제안만 하고 사용자가 수락해야 실행)과 AI_DIRECT(AI가 즉시 실행) 두 가지입니다.
            - 답변은 한국어로, 간결하게 합니다.
            """;


    private final ChatClient chatbotClient;
    private final CoreClient coreClient;
    private final StringRedisTemplate redisTemplate;

    public Flux<String> streamChat(Long groupId, Long userId, Long locationId, String message) {

        if (locationId != null) {
            LocationResponse location = coreClient.getLocation(locationId);

            if (!groupId.equals(location.groupId())) {
                throw new ForbiddenException("해당 그룹 소속의 위치가 아닙니다. locationId:" + locationId);
            }
        }

        Map<String, Object> context = new HashMap<>();
        context.put("groupId", groupId);
        context.put("userId", userId);
        if (locationId != null) {
            context.put("locationId", locationId);
        }

        String conversationId = "chat:" + groupId + ":" + userId + (locationId != null ? ":" + locationId : "");
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(OffsetDateTime.now());

        Flux<String> chatFlux = chatbotClient.prompt()
                .system(systemPrompt)
                .user(message)
                .toolContext(context)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();

        // 동일 conversationId에 대한 ChatMemory read-modify-write(조회 후 전체 교체)가
        // 병렬 요청 간에 겹치면 한쪽 turn이 유실될 수 있어, 대화 단위로 직렬화.
        String lockKey = LOCK_KEY_PREFIX + conversationId;
        String lockValue = UUID.randomUUID().toString();

        return acquireLock(lockKey, lockValue)
                .thenMany(chatFlux)
                .doFinally(signalType -> releaseLock(lockKey, lockValue));
    }

    private Mono<Void> acquireLock(String lockKey, String lockValue) {
        return Mono.fromCallable(() -> Boolean.TRUE.equals(
                        redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL)))
                .flatMap(acquired -> acquired ? Mono.<Void>empty() : Mono.error(new ConversationLockedException()))
                .retryWhen(Retry.fixedDelay(LOCK_MAX_RETRIES, LOCK_RETRY_DELAY)
                        .filter(ConversationLockedException.class::isInstance));
    }

    private void releaseLock(String lockKey, String lockValue) {
        if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
            redisTemplate.delete(lockKey);
        }
    }

    private static final class ConversationLockedException extends RuntimeException {
    }
}
