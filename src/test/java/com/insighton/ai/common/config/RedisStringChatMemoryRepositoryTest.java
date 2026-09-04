package com.insighton.ai.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * 도구 호출이 섞인 대화를 저장했다가 다시 불러왔을 때 toolCalls/toolResponses가 유실되지 않는지 확인한다 -
 * 이게 유실되면 다음 LLM 호출에 "도구 응답인데 대응하는 도구 호출 기록이 없는" 대화가 올라가 챗봇이
 * 도구를 한 번이라도 쓴 뒤부터 계속 실패하던 원인이었다.
 */
@ExtendWith(MockitoExtension.class)
class RedisStringChatMemoryRepositoryTest {

    private static final String CONVERSATION_ID = "chat:5:1";
    private static final String KEY = "chat-memory:" + CONVERSATION_ID;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisStringChatMemoryRepository repository;
    private final AtomicReference<String> savedJson = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = new RedisStringChatMemoryRepository(redisTemplate, new JsonMapper());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        doAnswer(invocation -> {
            savedJson.set(invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(eq(KEY), anyString(), any(Duration.class));
        given(valueOperations.get(KEY)).willAnswer(invocation -> savedJson.get());
    }

    @Test
    void 도구_호출이_있는_AssistantMessage는_toolCalls를_보존한_채_복원된다() {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-1", "function", "getWeather", "{}");
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();

        repository.saveAll(CONVERSATION_ID, List.of(assistantMessage));
        List<Message> reloaded = repository.findByConversationId(CONVERSATION_ID);

        assertThat(reloaded).hasSize(1);
        AssistantMessage reloadedAssistant = (AssistantMessage) reloaded.get(0);
        assertThat(reloadedAssistant.hasToolCalls()).isTrue();
        assertThat(reloadedAssistant.getToolCalls()).containsExactly(toolCall);
    }

    @Test
    void ToolResponseMessage는_UserMessage로_뭉개지지_않고_원래_타입으로_복원된다() {
        ToolResponseMessage.ToolResponse response =
                new ToolResponseMessage.ToolResponse("call-1", "getWeather", "{\"temp\":23.5}");
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(response))
                .build();

        repository.saveAll(CONVERSATION_ID, List.of(toolResponseMessage));
        List<Message> reloaded = repository.findByConversationId(CONVERSATION_ID);

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0)).isInstanceOf(ToolResponseMessage.class);
        assertThat(((ToolResponseMessage) reloaded.get(0)).getResponses()).containsExactly(response);
    }
}
