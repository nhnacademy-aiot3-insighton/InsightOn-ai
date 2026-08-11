package com.insighton.ai.domain.suggestion.tool;

import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuggestionChatTool {

    private final SuggestionLogService suggestionLogService;

    @Tool(description = "현재 그룹의 AI 제안 이력을 조회한다. 대화에 위치가 지정돼 있으면 그 위치로 제한된다. "
            + "from/to를 지정 안 하면 최근 1개월로 조회한다.")
    public List<SuggestionLogResponse> getSuggestions(
            @ToolParam(description = "조회 시작 시각(ISO-8601). 지정 안 하면 1개월 전부터", required = false) OffsetDateTime from,
            @ToolParam(description = "조회 종료 시각(ISO-8601). 지정 안 하면 지금까지", required = false) OffsetDateTime to,
            ToolContext toolContext
    ) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long locationId = (Long) toolContext.getContext().get("locationId");

        return suggestionLogService.findSuggestionLogs(groupId, locationId, from, to);
    }

    @Tool(description = "제안로그 ID로 제안 상세 본문(제안 문구, 수락 여부)을 조회한다.")
    public SuggestionLogResponse getSuggestion(
            @ToolParam(description = "조회할 제안로그 ID") Long suggestionLogId,
            ToolContext toolContext
    ) {
        Long userId = (Long) toolContext.getContext().get("userId");
        return suggestionLogService.findSuggestionLog(suggestionLogId, userId);
    }
}
