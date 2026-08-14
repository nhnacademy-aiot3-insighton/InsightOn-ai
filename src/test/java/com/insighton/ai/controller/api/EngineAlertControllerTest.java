package com.insighton.ai.controller.api;


import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.exception.EngineAlertNotFoundException;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EngineAlertController.class)
class EngineAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EngineAlertService engineAlertService;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void getEngineAlerts_정상_조회시_200_목록반환() throws Exception {
        EngineAlertResponse response = new EngineAlertResponse(1L, 5L, 42L, 7L, "제목", "메시지", Severity.CRITICAL,
                Map.of("temperature", 29.5), OffsetDateTime.now());

        given(engineAlertService.getEngineAlerts(5L, null, null, null, null, 0, 20))
                .willReturn(List.of(response));

        given(engineAlertService.countEngineAlerts(5L, null, null, null, null))
                .willReturn(1L);

        mockMvc.perform(get("/api/v1/engine-alerts").param("groupId", "5").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].engineAlertId").value(1))
                .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getEngineAlerts_X_User_Id_헤더가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/engine-alerts").param("groupId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEngineAlerts_groupId_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/engine-alerts").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEngineAlert_정상_조회시_200과_상세를_반환한다() throws Exception {
        EngineAlertResponse response = new EngineAlertResponse(1L, 5L, 42L, 7L, "제목", "메시지", Severity.WARNING,
                Map.of(), OffsetDateTime.now());
        given(engineAlertService.getEngineAlert(1L, 100L)).willReturn(response);

        mockMvc.perform(get("/api/v1/engine-alerts/1").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAlertId").value(1))
                .andExpect(jsonPath("$.severity").value("WARNING"));
    }

    @Test
    void getEngineAlert_존재하지_않으면_404를_반환한다() throws Exception {
        given(engineAlertService.getEngineAlert(999L, 100L)).willThrow(new EngineAlertNotFoundException(999L));

        mockMvc.perform(get("/api/v1/engine-alerts/999").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEngineAlert_그룹_멤버가_아니면_403을_반환한다() throws Exception {
        given(engineAlertService.getEngineAlert(1L, 100L)).willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        mockMvc.perform(get("/api/v1/engine-alerts/1").header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }
}
