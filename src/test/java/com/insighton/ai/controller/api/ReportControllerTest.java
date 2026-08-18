package com.insighton.ai.controller.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.exception.ReportNotFoundException;
import com.insighton.ai.domain.report.service.ReportService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void getReports_정상_조회시_200과_목록을_반환한다() throws Exception {
        ReportListResponse response = new ReportListResponse(1L, 5L, 42L, "제목", ReportType.WEEKLY,
                OffsetDateTime.now());
        given(reportService.findReports(5L, null, null, null, null, 0, 20)).willReturn(List.of(response));
        given(reportService.countReports(5L, null, null, null, null)).willReturn(1L);

        mockMvc.perform(get("/api/v1/reports").param("groupId", "5").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reportId").value(1))
                .andExpect(jsonPath("$.content[0].title").value("제목"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReports_X_User_Id_헤더가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports").param("groupId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReports_groupId가_없으면_400반환() throws Exception {
        mockMvc.perform(get("/api/v1/reports").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReports_정상_조회시_200_상세반환() throws Exception {
        ReportDetailResponse response = new ReportDetailResponse(1L, 5L, 42L, "제목", ReportType.WEEKLY, "본문",
                OffsetDateTime.now());

        given(reportService.findReport(1L, 100L)).willReturn(response);

        mockMvc.perform(get("/api/v1/reports/1").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(1))
                .andExpect(jsonPath("$.content").value("본문"));
    }

    @Test
    void getReport_존재하지_않으면_400반환() throws Exception {
        given(reportService.findReport(999L, 100L)).willThrow(new ReportNotFoundException(999L));

        mockMvc.perform(get("/api/v1/reports/999").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReport_그룹_멤버가_아니면_403을_반환한다() throws Exception {
        given(reportService.findReport(1L, 100L)).willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        mockMvc.perform(get("/api/v1/reports/1").header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }
}
