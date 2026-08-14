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

    @Test
    void getSuggestions_toolContext의_groupId_locationId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", null, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.findSuggestionLogs(5L, 42L, null, null, PageRequest.of(0, 50)))
                .willReturn(List.of(response));

        List<SuggestionLogResponse> result = suggestionChatTool.getSuggestions(null, null, toolContext);

        assertThat(result).containsExactly(response);
    }

    @Test
    void getSuggestions_locationId가_context에_없으면_null로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(suggestionLogService.findSuggestionLogs(5L, null, null, null, PageRequest.of(0, 50)))
                .willReturn(List.of());

        List<SuggestionLogResponse> result = suggestionChatTool.getSuggestions(null, null, toolContext);

        assertThat(result).isEmpty();
    }

    @Test
    void getSuggestion_toolContext의_userId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("userId", 100L));
        SuggestionLogResponse response = new SuggestionLogResponse(1L, 5L, 42L, "제목", "제안 내용", true, "{}",
                OffsetDateTime.now());
        given(suggestionLogService.findSuggestionLog(1L, 100L)).willReturn(response);

        SuggestionLogResponse result = suggestionChatTool.getSuggestion(1L, toolContext);

        assertThat(result).isEqualTo(response);
    }
}
