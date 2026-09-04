package com.insighton.ai.common.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일반 redis로 동작하는 ChatMemoryRepository 구현체
 */
@Component
public class RedisStringChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat-memory:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public RedisStringChatMemoryRepository(StringRedisTemplate redisTemplate,
                                           JsonMapper jsonMapper) {

        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<String> findConversationIds() {
        return redisTemplate.keys(KEY_PREFIX + "*").stream()
                .map(key -> key.substring(KEY_PREFIX.length()))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return findRawByConversationId(conversationId).stream()
                .map(RedisStringChatMemoryRepository::toMessage)
                .toList();
    }

    /**
     * 채팅 이력 조회 API용. 원본 타입(및 도구 호출/응답 원본 데이터)을 그대로 받는다.
     */
    public List<StoredMessage> findRawByConversationId(String conversationId) {
        String json = redisTemplate.opsForValue()
                .get(KEY_PREFIX + conversationId);

        if (json == null) {
            return List.of();
        }

        return Arrays.asList(jsonMapper.readValue(json, StoredMessage[].class));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        StoredMessage[] stored = messages.stream()
                .map(RedisStringChatMemoryRepository::toStoredMessage)
                .toArray(StoredMessage[]::new);

        String json = jsonMapper.writeValueAsString(stored);

        redisTemplate.opsForValue().set(KEY_PREFIX + conversationId, json, TTL);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /**
     * type+text만 저장하면, 도구 호출이 섞인 대화(챗봇 Tool 대부분이 여기 해당)를 다시 불러올 때
     * AssistantMessage의 toolCalls와 ToolResponseMessage의 실제 응답 내용이 통째로 사라진다. 그 상태로
     * 다음 LLM 호출에 이 이력을 다시 태우면 "도구 응답인데 대응하는 도구 호출 기록이 없는" 구조가 되어
     * Gemini가 대화를 거부하거나 이상하게 반응할 수 있다 - 도구를 한 번이라도 쓴 대화가 그 다음부터
     * 계속 깨지던 원인. toolCalls/toolResponses를 같이 저장해 그대로 복원한다.
     */
    private static StoredMessage toStoredMessage(Message message) {
        if (message instanceof AssistantMessage assistantMessage) {
            return new StoredMessage(MessageType.ASSISTANT, assistantMessage.getText(),
                    assistantMessage.hasToolCalls() ? assistantMessage.getToolCalls() : null, null);
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return new StoredMessage(MessageType.TOOL, message.getText(), null, toolResponseMessage.getResponses());
        }
        return new StoredMessage(message.getMessageType(), message.getText(), null, null);
    }

    private static Message toMessage(StoredMessage stored) {
        return switch (stored.type()) {
            case USER -> new UserMessage(stored.text());
            case ASSISTANT -> AssistantMessage.builder()
                    .content(stored.text())
                    .toolCalls(stored.toolCalls() != null ? stored.toolCalls() : List.of())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(stored.toolResponses() != null ? stored.toolResponses() : List.of())
                    .build();
            case SYSTEM -> new SystemMessage(stored.text());
        };
    }

    public record StoredMessage(
            MessageType type,
            String text,
            List<AssistantMessage.ToolCall> toolCalls,
            List<ToolResponseMessage.ToolResponse> toolResponses
    ) {
    }
}
