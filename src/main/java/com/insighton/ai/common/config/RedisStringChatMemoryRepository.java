package com.insighton.ai.common.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
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
        String json = redisTemplate.opsForValue()
                .get(KEY_PREFIX + conversationId);

        if (json == null) {
            return List.of();
        }

        StoredMessage[] stored = jsonMapper.readValue(json, StoredMessage[].class);

        return Arrays.stream(stored)
                .map(RedisStringChatMemoryRepository::toMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        StoredMessage[] stored = messages.stream()
                .map(m -> new StoredMessage(m.getMessageType(), m.getText()))
                .toArray(StoredMessage[]::new);

        String json = jsonMapper.writeValueAsString(stored);

        redisTemplate.opsForValue().set(KEY_PREFIX + conversationId, json, TTL);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    private static Message toMessage(StoredMessage stored) {
        return switch (stored.type()) {
            case USER, TOOL -> new UserMessage(stored.text());
            case ASSISTANT -> new AssistantMessage(stored.text());
            case SYSTEM -> new SystemMessage(stored.text());
        };
    }

    private record StoredMessage(MessageType type, String text) {
    }
}
