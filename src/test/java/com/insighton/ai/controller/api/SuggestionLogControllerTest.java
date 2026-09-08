package com.insighton.ai.controller.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.exception.SuggestionLogNotFoundException;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SuggestionLogController.class)
class SuggestionLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuggestionLogService suggestionLogService;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void getSuggestionLogs_정상_조회시_200과_목록을_반환한다() throws Exception {
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", null, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.findSuggestionLogs(5L, null, null, null, PageRequest.of(0, 20)))
                .willReturn(List.of(response));
        given(suggestionLogService.countSuggestionLogs(5L, null, null, null)).willReturn(1L);

        mockMvc.perform(get("/api/v1/suggestions").param("groupId", "5").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].suggestionLogId").value(1))
                .andExpect(jsonPath("$.content[0].isAccepted").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSuggestionLogs_X_User_Id_헤더가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").param("groupId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSuggestionLogs_groupId가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSuggestionLog_정상_조회시_200과_상세를_반환한다() throws Exception {
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", true, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.findSuggestionLog(1L, 100L)).willReturn(response);

        mockMvc.perform(get("/api/v1/suggestions/1").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestionLogId").value(1))
                .andExpect(jsonPath("$.isAccepted").value(true));
    }

    @Test
    void getSuggestionLog_존재하지_않으면_404를_반환한다() throws Exception {
        given(suggestionLogService.findSuggestionLog(999L, 100L))
                .willThrow(new SuggestionLogNotFoundException(999L));

        mockMvc.perform(get("/api/v1/suggestions/999").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSuggestionLog_그룹_멤버가_아니면_403을_반환한다() throws Exception {
        given(suggestionLogService.findSuggestionLog(1L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        mockMvc.perform(get("/api/v1/suggestions/1").header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accept_정상_처리시_200과_수락된_제안을_반환한다() throws Exception {
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", true, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.accept(1L, 100L)).willReturn(response);

        mockMvc.perform(post("/api/v1/suggestions/1/accept").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAccepted").value(true));
    }

    @Test
    void accept_존재하지_않으면_404를_반환한다() throws Exception {
        given(suggestionLogService.accept(999L, 100L)).willThrow(new SuggestionLogNotFoundException(999L));

        mockMvc.perform(post("/api/v1/suggestions/999/accept").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reject_정상_처리시_200과_거절된_제안을_반환한다() throws Exception {
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", false, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.reject(1L, 100L)).willReturn(response);

        mockMvc.perform(post("/api/v1/suggestions/1/reject").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAccepted").value(false));
    }

    @Test
    void reject_존재하지_않으면_404를_반환한다() throws Exception {
        given(suggestionLogService.reject(999L, 100L)).willThrow(new SuggestionLogNotFoundException(999L));

        mockMvc.perform(post("/api/v1/suggestions/999/reject").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }
}