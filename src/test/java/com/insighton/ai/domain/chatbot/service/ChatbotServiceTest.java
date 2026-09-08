package com.insighton.ai.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.config.RedisStringChatMemoryRepository;
import com.insighton.ai.common.config.RedisStringChatMemoryRepository.StoredMessage;
import com.insighton.ai.domain.chatbot.dto.ChatHistoryMessage;
import com.insighton.ai.domain.chatbot.exception.ConversationBusyException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(chatbotClient, coreClient, redisTemplate, chatMemoryRepository);
    }

    /**
     * chatbotClient.prompt()...stream().content() 빌더 체인을 스텁한다. streamChat()은 락 성공/실패와 무관하게 이 체인을 항상 먼저 조립하므로(실제 구독은 락
     * 획득 이후), 락 실패 테스트에서도 이 스텁이 필요하다.
     */
    private void stubChatClient(Flux<String> content) {
        given(chatbotClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.toolContext(any())).willReturn(requestSpec);
        given(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
        given(streamResponseSpec.content()).willReturn(content);
    }

    @Test
    void streamChat_다른_그룹_소속_위치면_구독_전에_ForbiddenException을_던진다() {
        given(coreClient.getLocation(42L)).willReturn(
                new LocationResponse(42L, "회의실", 999L, AutoControlMode.SUGGESTION));

        assertThatThrownBy(() -> chatbotService.streamChat(5L, 100L, 42L, "메시지"))
                .isInstanceOf(ForbiddenException.class);

        verify(chatbotClient, never()).prompt();
    }

    @Test
    void streamChat_락을_획득하면_토큰을_스트리밍하고_완료_시_락을_해제한다() {
        stubChatClient(Flux.just("안녕", "하세요"));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        ArgumentCaptor<String> lockValueCaptor = ArgumentCaptor.forClass(String.class);
        given(valueOperations.setIfAbsent(eq("chat-lock:chat:5:100"), lockValueCaptor.capture(),
                eq(Duration.ofSeconds(60))))
                .willReturn(true);
        given(valueOperations.get("chat-lock:chat:5:100")).willAnswer(inv -> lockValueCaptor.getValue());

        Flux<String> result = chatbotService.streamChat(5L, 100L, null, "안녕");

        StepVerifier.create(result)
                .expectNext("안녕", "하세요")
                .verifyComplete();

        verify(redisTemplate).delete("chat-lock:chat:5:100");
    }

    @Test
    void streamChat_위치가_그룹_소속이면_통과하고_정상적으로_스트리밍한다() {
        given(coreClient.getLocation(42L)).willReturn(new LocationResponse(42L, "회의실", 5L, AutoControlMode.SUGGESTION));
        stubChatClient(Flux.just("네"));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).willReturn(true);

        Flux<String> result = chatbotService.streamChat(5L, 100L, 42L, "에어컨 상태 알려줘");

        StepVerifier.create(result)
                .expectNext("네")
                .verifyComplete();
    }

    @Test
    void streamChat_락_획득에_계속_실패하면_재시도_끝에_ConversationBusyException을_던진다() {
        stubChatClient(Flux.just("안 나올 응답"));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).willReturn(false);

        StepVerifier.withVirtualTime(() -> chatbotService.streamChat(5L, 100L, null, "메시지"))
                .thenAwait(Duration.ofSeconds(15))
                .expectError(ConversationBusyException.class)
                .verify();
    }

    @Test
    void getHistory_위치와_무관하게_userId_기준_conversationId로_조회한다() {
        given(chatMemoryRepository.findRawByConversationId("chat:5:100")).willReturn(List.of(
                new StoredMessage(MessageType.USER, "안녕", null, null),
                new StoredMessage(MessageType.ASSISTANT, "안녕하세요", null, null)));

        List<ChatHistoryMessage> history = chatbotService.getHistory(5L, 100L);

        assertThat(history).containsExactly(
                new ChatHistoryMessage("USER", "안녕"),
                new ChatHistoryMessage("ASSISTANT", "안녕하세요"));
    }

    @Test
    void getHistory_TOOL과_SYSTEM_메시지는_제외한다() {
        given(chatMemoryRepository.findRawByConversationId("chat:5:100")).willReturn(List.of(
                new StoredMessage(MessageType.SYSTEM, "시스템 프롬프트", null, null),
                new StoredMessage(MessageType.USER, "에어컨 꺼줘", null, null),
                new StoredMessage(MessageType.TOOL, "{\"actuatorType\":\"AIRCON\"}", null, null),
                new StoredMessage(MessageType.ASSISTANT, "껐습니다", null, null)));

        List<ChatHistoryMessage> history = chatbotService.getHistory(5L, 100L);

        assertThat(history).containsExactly(
                new ChatHistoryMessage("USER", "에어컨 꺼줘"),
                new ChatHistoryMessage("ASSISTANT", "껐습니다"));
    }
}
