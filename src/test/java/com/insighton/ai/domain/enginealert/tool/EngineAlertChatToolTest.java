package com.insighton.ai.domain.enginealert.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
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
class EngineAlertChatToolTest {

    @Mock
    private EngineAlertService engineAlertService;

    @InjectMocks
    private EngineAlertChatTool engineAlertChatTool;

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-01T10:30:00+09:00");

    @Test
    void getAlerts_toolContext의_groupId_locationId로_조회해_한줄_문자열로_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        EngineAlertResponse response = new EngineAlertResponse(1L, 5L, 42L, 7L, "제목", "메시지", Severity.CRITICAL,
                Map.of(), CREATED_AT);
        given(engineAlertService.getEngineAlerts(5L, 42L, Severity.CRITICAL, null, null, 0, 50))
                .willReturn(List.of(response));

        String result = engineAlertChatTool.getAlerts(Severity.CRITICAL, null, null, toolContext);

        assertThat(result).isEqualTo("id=1 | loc=42 | CRITICAL | 제목 | 2026-08-01 10:30");
    }

    @Test
    void getAlerts_결과가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(engineAlertService.getEngineAlerts(5L, null, null, null, null, 0, 50)).willReturn(List.of());

        String result = engineAlertChatTool.getAlerts(null, null, null, toolContext);

        assertThat(result).isEqualTo("조회된 엔진 알람 없음");
    }

    @Test
    void getEngineAlert_toolContext의_userId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("userId", 100L));
        EngineAlertResponse response = new EngineAlertResponse(1L, 5L, 42L, 7L, "제목", "메시지", Severity.WARNING,
                Map.of(), CREATED_AT);
        given(engineAlertService.getEngineAlert(1L, 100L)).willReturn(response);

        EngineAlertResponse result = engineAlertChatTool.getEngineAlert(1L, toolContext);

        assertThat(result).isEqualTo(response);
    }
}
