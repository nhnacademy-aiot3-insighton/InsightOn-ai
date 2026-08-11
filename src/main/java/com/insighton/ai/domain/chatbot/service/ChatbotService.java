package com.insighton.ai.domain.chatbot.service;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatbotService {

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

        return chatbotClient.prompt()
                .system(systemPrompt)
                .user(message)
                .toolContext(context)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();


    }
}
