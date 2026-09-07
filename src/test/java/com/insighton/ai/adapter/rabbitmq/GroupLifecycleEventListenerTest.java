package com.insighton.ai.adapter.rabbitmq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.rabbitmq.dto.GroupDeletedEvent;
import com.insighton.ai.adapter.rabbitmq.dto.LocationDeletedEvent;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.report.service.ReportService;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupLifecycleEventListenerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private SuggestionLogService suggestionLogService;

    @Mock
    private EngineAlertService engineAlertService;

    @Mock
    private DashboardNotificationService dashboardNotificationService;

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    private GroupLifecycleEventListener groupLifecycleEventListener;

    @BeforeEach
    void setUp() {
        groupLifecycleEventListener = new GroupLifecycleEventListener(reportService, suggestionLogService,
                engineAlertService, dashboardNotificationService, hourlyTelemetryStatService);
    }

    @Test
    void handleGroupDeleted_위치가_있으면_전부_일괄_삭제한다() {
        GroupDeletedEvent event = new GroupDeletedEvent(5L, List.of(1L, 2L));

        groupLifecycleEventListener.handleGroupDeleted(event);

        verify(reportService).deleteByGroup(5L);
        verify(suggestionLogService).deleteByGroup(5L);
        verify(engineAlertService).deleteByGroup(5L);
        verify(dashboardNotificationService).deleteByGroup(5L);
        verify(hourlyTelemetryStatService).deleteByLocations(List.of(1L, 2L));
    }

    @Test
    void handleGroupDeleted_위치가_없으면_텔레메트리_삭제를_건너뛴다() {
        GroupDeletedEvent event = new GroupDeletedEvent(5L, List.of());

        groupLifecycleEventListener.handleGroupDeleted(event);

        verify(reportService).deleteByGroup(5L);
        verify(hourlyTelemetryStatService, never()).deleteByLocations(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleLocationDeleted_위치_기준으로_전부_삭제한다() {
        LocationDeletedEvent event = new LocationDeletedEvent(42L);

        groupLifecycleEventListener.handleLocationDeleted(event);

        verify(reportService).deleteByLocation(42L);
        verify(suggestionLogService).deleteByLocation(42L);
        verify(engineAlertService).deleteByLocation(42L);
        verify(dashboardNotificationService).deleteByLocation(42L);
        verify(hourlyTelemetryStatService).deleteByLocation(42L);
    }
}
