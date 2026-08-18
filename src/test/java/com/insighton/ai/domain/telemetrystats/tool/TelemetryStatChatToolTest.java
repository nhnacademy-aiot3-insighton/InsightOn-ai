package com.insighton.ai.domain.telemetrystats.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class TelemetryStatChatToolTest {

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private TelemetryStatChatTool telemetryStatChatTool;

    private static final PeriodTelemetrySummary SUMMARY = new PeriodTelemetrySummary(42L, null, null,
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    @Test
    void getStats_locationName이_없으면_context의_locationId를_사용한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now();
        given(hourlyTelemetryStatService.summarizePeriod(42L, from, to)).willReturn(SUMMARY);

        Object result = telemetryStatChatTool.getStats(null, from, to, toolContext);

        assertThat(result).isEqualTo(SUMMARY);
    }

    @Test
    void getStats_locationName이_정확히_일치하면_해당_위치로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(42L, "사무실1", 5L, AutoControlMode.SUGGESTION),
                new LocationResponse(99L, "사무실2", 5L, AutoControlMode.SUGGESTION)));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(SUMMARY);

        Object result = telemetryStatChatTool.getStats("사무실1", null, null, toolContext);

        assertThat(result).isEqualTo(SUMMARY);
    }

    @Test
    void getStats_locationName이_대소문자_무시_부분일치하면_해당_위치로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(42L, "1F Office", 5L, AutoControlMode.SUGGESTION)));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(SUMMARY);

        Object result = telemetryStatChatTool.getStats("office", null, null, toolContext);

        assertThat(result).isEqualTo(SUMMARY);
    }

    @Test
    void getStats_locationName과_일치하는_위치가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(
                new LocationResponse(42L, "사무실1", 5L, AutoControlMode.SUGGESTION)));

        Object result = telemetryStatChatTool.getStats("없는위치", null, null, toolContext);

        assertThat(result).isEqualTo("위치를 찾을 수 없습니다: 없는위치");
    }

    @Test
    void getStats_locationName도_없고_context에_locationId도_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        Object result = telemetryStatChatTool.getStats(null, null, null, toolContext);

        assertThat(result).isEqualTo("이 대화에서 어느 위치를 말하는지 알 수 없어 센서 통계를 조회할 수 없습니다. "
                + "사용자에게 어느 위치의 통계를 원하는지 물어보세요.");
    }

    @Test
    void getStats_from_to를_지정하지_않으면_최근_7일로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        given(hourlyTelemetryStatService.summarizePeriod(eq(42L), any(), any())).willReturn(SUMMARY);

        telemetryStatChatTool.getStats(null, null, null, toolContext);

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(hourlyTelemetryStatService).summarizePeriod(eq(42L), fromCaptor.capture(), toCaptor.capture());

        assertThat(fromCaptor.getValue()).isCloseTo(OffsetDateTime.now().minusDays(7), byLessThan(1, ChronoUnit.MINUTES));
        assertThat(toCaptor.getValue()).isCloseTo(OffsetDateTime.now(), byLessThan(1, ChronoUnit.MINUTES));
    }
}
