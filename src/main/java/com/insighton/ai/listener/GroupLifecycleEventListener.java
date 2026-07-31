package com.insighton.ai.listener;

import com.insighton.ai.common.dto.GroupDeletedEvent;
import com.insighton.ai.common.dto.LocationDeletedEvent;
import com.insighton.ai.config.RabbitConfig;
import com.insighton.ai.enginealert.service.EngineAlertService;
import com.insighton.ai.notification.service.DashboardNotificationService;
import com.insighton.ai.report.service.ReportService;
import com.insighton.ai.suggestion.service.SuggestionLogService;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GroupLifecycleEventListener {

    private final ReportService reportService;
    private final SuggestionLogService suggestionLogService;
    private final EngineAlertService engineAlertService;
    private final DashboardNotificationService dashboardNotificationService;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;

    @RabbitListener(queues = RabbitConfig.GROUP_DELETED_QUEUE)
    public void handleGroupDeleted(GroupDeletedEvent event) {
        log.info("GroupDeleted 이벤트 수신 - groupId:{}, locationIds:{}", event.groupId(), event.locationIds());

        reportService.deleteByGroup(event.groupId());
        suggestionLogService.deleteByGroup(event.groupId());
        engineAlertService.deleteByGroup(event.groupId());
        dashboardNotificationService.deleteByGroup(event.groupId());
        hourlyTelemetryStatService.deleteByLocations(event.locationIds());

        log.info("GroupDeleted 처리 완료 - groupId: {}", event.groupId());
    }

    @RabbitListener(queues = RabbitConfig.LOCATION_DELETED_QUEUE)
    public void handleLocationDeleted(LocationDeletedEvent event) {
        log.info("LocationDeleted 이벤트 수신 - locationId:{}", event.locationId());

        reportService.deleteByLocation(event.locationId());
        suggestionLogService.deleteByLocation(event.locationId());
        engineAlertService.deleteByLocation(event.locationId());
        dashboardNotificationService.deleteByLocation(event.locationId());
        hourlyTelemetryStatService.deleteByLocation(event.locationId());

        log.info("LocationDeleted 처리 완료 - locationId:{}", event.locationId());
    }
}
