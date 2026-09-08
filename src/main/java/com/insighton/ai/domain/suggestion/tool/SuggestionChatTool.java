package com.insighton.ai.domain.suggestion.tool;

import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuggestionChatTool {

    private static final int MAX_RESULTS = 50;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SuggestionLogService suggestionLogService;

    @Tool(description = "현재 그룹의 AI 제안 이력을 조회한다. 대화에 위치가 지정돼 있으면 그 위치로 제한된다. "
            + "from/to를 지정 안 하면 최근 1개월로 조회한다. "
            + "결과는 'id=제안ID | loc=위치ID | 상태 | 제목 | 제안내용 | 생성일시' 형식의 한 줄씩이다. 상태는 대기/수락/거절 중 하나.")
    public String getSuggestions(
            @ToolParam(description = "조회 시작 시각(ISO-8601). 지정 안 하면 1개월 전부터", required = false) OffsetDateTime from,
            @ToolParam(description = "조회 종료 시각(ISO-8601). 지정 안 하면 지금까지", required = false) OffsetDateTime to,
            ToolContext toolContext
    ) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long locationId = (Long) toolContext.getContext().get("locationId");

        var suggestions = suggestionLogService.findSuggestionLogs(groupId, locationId, from, to,
                PageRequest.of(0, MAX_RESULTS));
        if (suggestions.isEmpty()) {
            return "조회된 제안 없음";
        }
        return suggestions.stream()
                .map(s -> "id=%d | loc=%d | %s | %s | %s | %s".formatted(
                        s.suggestionLogId(), s.locationId(), status(s.isAccepted()), s.title(), s.suggestionText(),
                        s.createdAt().format(DATE_FORMAT)))
                .collect(Collectors.joining("\n"));
    }

    private String status(Boolean isAccepted) {
        if (isAccepted == null) {
            return "대기";
        }
        return isAccepted ? "수락" : "거절";
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
