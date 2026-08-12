package com.insighton.ai.domain.enginealert.tool;

import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EngineAlertChatTool {

    private static final int MAX_RESULTS = 50;

    private final EngineAlertService engineAlertService;

    @Tool(description = "현재 그룹의 엔진 알람 목록을 조회한다. 대화에 위치가 지정돼 있으면 그 위치로 제한된다. "
            + "from/to를 지정 안 하면 최근 1개월로 조회한다.")
    public List<EngineAlertResponse> getAlerts(
            @ToolParam(description = "심각도로 필터링. INFO/WARNING/CRITICAL, 지정 안 하면 전체", required = false) Severity severity,
            @ToolParam(description = "조회 시작 시각(ISO-8601). 지정 안 하면 1개월 전부터", required = false) OffsetDateTime from,
            @ToolParam(description = "조회 종료 시각(ISO-8601). 지정 안 하면 지금까지", required = false) OffsetDateTime to,
            ToolContext toolContext
    ) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long locationId = (Long) toolContext.getContext().get("locationId");
        return engineAlertService.getEngineAlerts(groupId, locationId, severity, from, to, 0, MAX_RESULTS);
    }

    @Tool(description = "엔진알람 ID로 엔진알람 상세 본문을 조회한다.")
    public EngineAlertResponse getEngineAlert(
            @ToolParam(description = "조회할 엔진알람 ID") Long engineAlertId,
            ToolContext toolContext
    ) {
        Long userId = (Long) toolContext.getContext().get("userId");

        return engineAlertService.getEngineAlert(engineAlertId, userId);
    }
}
