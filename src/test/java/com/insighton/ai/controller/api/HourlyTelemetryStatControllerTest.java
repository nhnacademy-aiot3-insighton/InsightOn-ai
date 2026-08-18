package com.insighton.ai.controller.api;


import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HourlyTelemetryStatController.class)
class HourlyTelemetryStatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void getHourlyTelemetryStatus_정상_조회시_200_목록_반환() throws Exception {
        HourlyTelemetryStatResponse response = new HourlyTelemetryStatResponse(1L, 42L, OffsetDateTime.now(),
                "{}", "{}", "{}", "{}", OffsetDateTime.now());

        given(hourlyTelemetryStatService.findHourlyTelemetryStats(5L, 42L, null, null, PageRequest.of(0, 20)))
                .willReturn(List.of(response));

        given(hourlyTelemetryStatService.countHourlyTelemetryStats(5L, 42L, null, null)).willReturn(1L);

        mockMvc.perform(get("/api/v1/hourly-telemetry-stats")
                        .param("groupId", "5").param("locationId", "42").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hourlyTelemetryStatId").value(1))
                .andExpect(jsonPath("$.content[0].locationId").value(42))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getHourlyTelemetryStatus_groupId가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/hourly-telemetry-stats").param("locationId", "42").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHourlyTelemetryStatus_locationId가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/hourly-telemetry-stats").param("groupId", "5").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHourlyTelemetryStatus_X_User_Id_헤더가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/hourly-telemetry-stats").param("groupId", "5").param("locationId", "42"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHourlyTelemetryStatus_다른_그룹_소속_위치면_403_반환() throws Exception {
        given(hourlyTelemetryStatService.findHourlyTelemetryStats(5L, 42L, null, null, PageRequest.of(0, 20)))
                .willThrow(new ForbiddenException("해당 그룹 소속의 위치가 아닙니다. locationId:42"));

        mockMvc.perform(get("/api/v1/hourly-telemetry-stats")
                        .param("groupId", "5").param("locationId", "42").header("X-User-Id", "1"))
                .andExpect(status().isForbidden());
    }
}
