package com.insighton.ai.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.common.config.RedisStringChatMemoryRepository;
import com.insighton.ai.common.config.RedisStringChatMemoryRepository.StoredMessage;
import com.insighton.ai.domain.chatbot.dto.ChatHistoryMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock
    private ChatClient chatbotClient;

    @Mock
    private CoreClient coreClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisStringChatMemoryRepository chatMemoryRepository;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(chatbotClient, coreClient, redisTemplate, chatMemoryRepository);
    }

    @Test
    void getHistory_위치와_무관하게_userId_기준_conversationId로_조회한다() {
        given(chatMemoryRepository.findRawByConversationId("chat:5:100")).willReturn(List.of(
                new StoredMessage(MessageType.USER, "안녕"),
                new StoredMessage(MessageType.ASSISTANT, "안녕하세요")));

        List<ChatHistoryMessage> history = chatbotService.getHistory(5L, 100L);

        assertThat(history).containsExactly(
                new ChatHistoryMessage("USER", "안녕"),
                new ChatHistoryMessage("ASSISTANT", "안녕하세요"));
    }

    @Test
    void getHistory_TOOL과_SYSTEM_메시지는_제외한다() {
        given(chatMemoryRepository.findRawByConversationId("chat:5:100")).willReturn(List.of(
                new StoredMessage(MessageType.SYSTEM, "시스템 프롬프트"),
                new StoredMessage(MessageType.USER, "에어컨 꺼줘"),
                new StoredMessage(MessageType.TOOL, "{\"actuatorType\":\"AIRCON\"}"),
                new StoredMessage(MessageType.ASSISTANT, "껐습니다")));

        List<ChatHistoryMessage> history = chatbotService.getHistory(5L, 100L);

        assertThat(history).containsExactly(
                new ChatHistoryMessage("USER", "에어컨 꺼줘"),
                new ChatHistoryMessage("ASSISTANT", "껐습니다"));
    }
}
