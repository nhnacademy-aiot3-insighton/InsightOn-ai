package com.insighton.ai.domain.suggestion.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SuggestionChatToolTest {

    @Mock
    private SuggestionLogService suggestionLogService;

    @InjectMocks
    private SuggestionChatTool suggestionChatTool;

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-01T10:30:00+09:00");

    @Test
    void getSuggestions_toolContext의_groupId_locationId로_조회해_한줄_문자열로_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", null, "{}",
                CREATED_AT);
        given(suggestionLogService.findSuggestionLogs(5L, 42L, null, null, PageRequest.of(0, 50)))
                .willReturn(List.of(response));

        String result = suggestionChatTool.getSuggestions(null, null, toolContext);

        assertThat(result).isEqualTo("id=1 | loc=42 | 대기 | 제목 | 제안 내용 | 2026-08-01 10:30");
    }

    @Test
    void getSuggestions_수락_거절_상태도_올바르게_표기한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        SuggestionLogResponse accepted = new SuggestionLogResponse(1L, 5L, 42L, "수락됨", "내용1", true, "{}",
                CREATED_AT);
        SuggestionLogResponse rejected = new SuggestionLogResponse(2L, 5L, 42L, "거절됨", "내용2", false, "{}",
                CREATED_AT);
        given(suggestionLogService.findSuggestionLogs(5L, null, null, null, PageRequest.of(0, 50)))
                .willReturn(List.of(accepted, rejected));

        String result = suggestionChatTool.getSuggestions(null, null, toolContext);

        assertThat(result).isEqualTo(
                "id=1 | loc=42 | 수락 | 수락됨 | 내용1 | 2026-08-01 10:30\n"
                        + "id=2 | loc=42 | 거절 | 거절됨 | 내용2 | 2026-08-01 10:30");
    }

    @Test
    void getSuggestions_결과가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(suggestionLogService.findSuggestionLogs(5L, null, null, null, PageRequest.of(0, 50)))
                .willReturn(List.of());

        String result = suggestionChatTool.getSuggestions(null, null, toolContext);

        assertThat(result).isEqualTo("조회된 제안 없음");
    }

    @Test
    void getSuggestion_toolContext의_userId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("userId", 100L));
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", true, "{}",
                CREATED_AT);
        given(suggestionLogService.findSuggestionLog(1L, 100L)).willReturn(response);

        SuggestionLogResponse result = suggestionChatTool.getSuggestion(1L, toolContext);

        assertThat(result).isEqualTo(response);
    }
}
