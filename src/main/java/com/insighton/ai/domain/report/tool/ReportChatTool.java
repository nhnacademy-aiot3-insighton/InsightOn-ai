package com.insighton.ai.domain.report.tool;

import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.service.ReportService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportChatTool {

    private final ReportService reportService;

    @Tool(description = "현재 그룹의 주간/월간 리포트 목록을 조회한다. 대화에 위치가 지정돼 있으면 그 위치로 제한된다. "
            + "'주간'은 WEEKLY, '월간'은 MONTHLY로 매핑한다. from/to를 지정 안 하면 최근 3개월로 조회한다.")
    public List<ReportListResponse> getReports(
            @ToolParam(description = "리포트 종류로 필터링. WEEKLY 또는 MONTHLY, 지정 안하면 전체", required = false)
            ReportType reportType,

            @ToolParam(description = "조회 시작 시각(ISO-8601). 지정 안 하면 3개월 전부터", required = false)
            OffsetDateTime from,

            @ToolParam(description = "조회 종료 시각(ISO-8601). 지정 안 하면 지금까지", required = false)
            OffsetDateTime to,
            ToolContext toolContext) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long locationId = (Long) toolContext.getContext().get("locationId");
        OffsetDateTime resolvedFrom = from != null ? from : OffsetDateTime.now().minusMonths(3);
        OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now();

        return reportService.findReports(groupId, locationId, reportType, resolvedFrom, resolvedTo);
    }

    @Tool(description = "리포트 ID로 리포트 상세 본문을 조회한다. 날짜로 리포트를 찾을 땐 먼저 getReports로 목록을 받아 "
            + "제목/생성일이 일치하는 항목의 ID를 확인한 뒤 호출한다.")
    public ReportDetailResponse getReportDetail(
            @ToolParam(description = "조회할 리포트 ID") Long reportId,
            ToolContext toolContext) {
        Long userId = (Long) toolContext.getContext().get("userId");
        return reportService.findReport(reportId, userId);
    }
}
