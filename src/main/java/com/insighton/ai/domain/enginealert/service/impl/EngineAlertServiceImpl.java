package com.insighton.ai.domain.enginealert.service.impl;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.event.EngineAlertActionEvent;
import com.insighton.ai.domain.enginealert.exception.EngineAlertNotFoundException;
import com.insighton.ai.domain.enginealert.repository.EngineAlertRepository;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineAlertServiceImpl implements EngineAlertService {

    private final EngineAlertRepository engineAlertRepository;
    private final Validator validator;
    private final GroupAuthorizationService groupAuthorizationService;
    private final DashboardNotificationService dashboardNotificationService;
    private final JsonMapper jsonMapper;

    @Override
    public List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId, Severity severity,
                                                     OffsetDateTime from, OffsetDateTime to, int offset, int limit) {

        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }

        List<EngineAlert> alerts = engineAlertRepository.search(groupId, locationId, severity, resolveFrom(from),
                resolveTo(to), offset, limit);
        log.info("엔진 알람 목록 조회 - groupId:{}, locationId:{}, size:{}", groupId, locationId, alerts.size());

        return alerts.stream()
                .map(EngineAlertResponse::from)
                .toList();
    }

    @Override
    public long countEngineAlerts(Long groupId, Long locationId, Severity severity, OffsetDateTime from,
                                  OffsetDateTime to) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }
        return engineAlertRepository.count(groupId, locationId, severity, resolveFrom(from), resolveTo(to));
    }

    private OffsetDateTime resolveFrom(OffsetDateTime from) {
        return from != null ? from : OffsetDateTime.now().minusMonths(1);
    }

    private OffsetDateTime resolveTo(OffsetDateTime to) {
        return to != null ? to : OffsetDateTime.now();
    }

    @Override
    public EngineAlertResponse getEngineAlert(Long engineAlertId, Long userId) {

        EngineAlert alert = engineAlertRepository.findById(engineAlertId)
                .orElseThrow(() -> new EngineAlertNotFoundException(engineAlertId));

        groupAuthorizationService.requireMembership(alert.getGroupId(), userId);

        log.info("엔진 알람 조회 - engineAlertId:{}", engineAlertId);
        return EngineAlertResponse.from(alert);
    }

    @Transactional
    @Override
    public void createEngineAlert(EngineAlertActionEvent event) {
        Set<ConstraintViolation<EngineAlertActionEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        String triggerValueJson = writeTriggerValueAsJson(event.triggerValue());

        Optional<Long> claimedId = engineAlertRepository.claimEventId(
                event.eventId(), event.groupId(), event.locationId(), event.flowId(),
                event.title(), event.message(), event.severity().name(), triggerValueJson);

        if (claimedId.isEmpty()) {
            log.info("이미 처리된 알람 이벤트, 스킵 - eventId:{}", event.eventId());
            return;
        }

        log.info("엔진 알람 생성 - engineAlertId:{}, severity:{}", claimedId.get(), event.severity());

        dashboardNotificationService.create(new DashboardNotificationCreateRequest(
                event.groupId(),
                event.locationId(),
                NotificationType.ENGINE_ALERT,
                claimedId.get(),
                event.title()
        ));
    }

    @Transactional
    @Override
    public void deleteByGroup(Long groupId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }
        engineAlertRepository.deleteByGroupId(groupId);
        log.info("엔진 알람 일괄 삭제 - groupId:{}", groupId);
    }

    @Transactional
    @Override
    public void deleteByLocation(Long locationId) {
        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }
        engineAlertRepository.deleteByLocationId(locationId);
        log.info("엔진 알람 일괄 삭제 - locationId:{}", locationId);
    }

    @Override
    public EngineAlertSummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to) {
        List<EngineAlert> alerts = engineAlertRepository.searchByPeriod(locationId, from, to);

        long criticalCount = alerts.stream().filter(a -> a.getSeverity() == Severity.CRITICAL).count();
        long warningCount = alerts.stream().filter(a -> a.getSeverity() == Severity.WARNING).count();

        List<String> topAlertTitles = alerts.stream()
                .filter(a -> a.getSeverity() == Severity.CRITICAL)
                .map(EngineAlert::getTitle)
                .limit(5)
                .toList();

        return new EngineAlertSummary(criticalCount, warningCount, topAlertTitles);
    }

    private String writeTriggerValueAsJson(Map<String, Object> triggerValue) {

        return jsonMapper.writeValueAsString(triggerValue);
    }
}