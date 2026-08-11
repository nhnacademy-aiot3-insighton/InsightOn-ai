package com.insighton.ai.domain.enginealert.service.impl;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineAlertServiceImpl implements EngineAlertService {

    private final EngineAlertRepository engineAlertRepository;
    private final Validator validator;
    private final GroupAuthorizationService groupAuthorizationService;
    private final DashboardNotificationService dashboardNotificationService;

    @Override
    public List<EngineAlertResponse> getEngineAlerts(Long groupId, Long locationId, Severity severity,
                                                     OffsetDateTime from, OffsetDateTime to) {

        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }

        List<EngineAlert> alerts = engineAlertRepository.search(groupId, locationId, severity, from, to);
        log.info("엔진 알람 목록 조회 - groupId:{}, locationId:{}, size:{}", groupId, locationId, alerts.size());

        return alerts.stream()
                .map(EngineAlertResponse::from)
                .toList();
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
    public EngineAlertResponse createEngineAlert(EngineAlertCreateRequest request) {
        Set<ConstraintViolation<EngineAlertCreateRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        EngineAlert alert = EngineAlert.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .flowId(request.flowId())
                .title(request.title())
                .message(request.message())
                .severity(request.severity())
                .triggerValue(request.triggerValue())
                .build();

        EngineAlert savedAlert = engineAlertRepository.save(alert);

        log.info("엔진 알람 생성 - engineAlertId:{}, severity:{}", savedAlert.getEngineAlertId(), savedAlert.getSeverity());

        dashboardNotificationService.create(new DashboardNotificationCreateRequest(
                savedAlert.getGroupId(),
                savedAlert.getLocationId(),
                NotificationType.ENGINE_ALERT,
                savedAlert.getEngineAlertId(),
                savedAlert.getTitle()
        ));

        return EngineAlertResponse.from(savedAlert);
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
}
