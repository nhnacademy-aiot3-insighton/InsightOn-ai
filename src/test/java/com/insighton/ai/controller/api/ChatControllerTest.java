package com.insighton.ai.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.chatbot.dto.ChatRequest;
import com.insighton.ai.domain.chatbot.service.ChatbotService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void chat_정상_요청시_SSE로_토큰_스트리밍() throws Exception {
        given(chatbotService.streamChat(5L, 100L, 42L, "안녕"))
                .willReturn(Flux.just("안녕", "하세요"));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat")
                        .param("groupId", "5").param("locationId", "42")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();

        String body = finalResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("안녕", "하세요");
    }

    @Test
    void chat_locationId_없이도_정상_요청() throws Exception {
        given(chatbotService.streamChat(5L, 100L, null, "안녕"))
                .willReturn(Flux.just("안녕"));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat")
                        .param("groupId", "5")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());
    }

    @Test
    void chat_groupId가_없으면_400_반환() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_X_User_Id_헤더가_없으면_400_반환() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .param("groupId", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_다른_그룹_소속_위치면_403_반환() throws Exception {
        given(chatbotService.streamChat(5L, 100L, 42L, "안녕"))
                .willThrow(new ForbiddenException("해당 그룹 소속의 위치가 아닙니다. locationId:42"));

        mockMvc.perform(post("/api/v1/chat")
                        .param("groupId", "5").param("locationId", "42")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(status().isForbidden());
    }
}