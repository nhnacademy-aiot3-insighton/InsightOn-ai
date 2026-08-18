package com.insighton.ai.domain.report.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class ReportChatToolTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportChatTool reportChatTool;

    @Test
    void getReports_toolContext의_groupId_locationId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        ReportListResponse response = new ReportListResponse(1L, 5L, 42L, "제목", ReportType.WEEKLY,
                OffsetDateTime.now());
        given(reportService.findReports(5L, 42L, ReportType.WEEKLY, null, null, 0, 50))
                .willReturn(List.of(response));

        List<ReportListResponse> result = reportChatTool.getReports(ReportType.WEEKLY, null, null, toolContext);

        assertThat(result).containsExactly(response);
    }

    @Test
    void getReports_locationId가_context에_없으면_null로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(reportService.findReports(5L, null, null, null, null, 0, 50)).willReturn(List.of());

        List<ReportListResponse> result = reportChatTool.getReports(null, null, null, toolContext);

        assertThat(result).isEmpty();
    }

    @Test
    void getReportDetail_toolContext의_userId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("userId", 100L));
        ReportDetailResponse response = new ReportDetailResponse(1L, 5L, 42L, "제목", ReportType.WEEKLY, "본문",
                OffsetDateTime.now());
        given(reportService.findReport(1L, 100L)).willReturn(response);

        ReportDetailResponse result = reportChatTool.getReportDetail(1L, toolContext);

        assertThat(result).isEqualTo(response);
    }
}
