package com.insighton.ai.domain.enginealert.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.dto.EngineAlertSummary;
import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.event.EngineAlertActionEvent;
import com.insighton.ai.domain.enginealert.exception.EngineAlertNotFoundException;
import com.insighton.ai.domain.enginealert.repository.EngineAlertRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EngineAlertServiceImplTest {

    @Mock
    private EngineAlertRepository engineAlertRepository;
    @Mock
    private Validator validator;
    @Mock
    private GroupAuthorizationService groupAuthorizationService;
    @Mock
    private DashboardNotificationService dashboardNotificationService;
    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private EngineAlertServiceImpl engineAlertService;

    private EngineAlert newAlert(Long id, Long groupId, Long locationId, Severity severity, String title) {
        EngineAlert alert = EngineAlert.builder()
                .eventId("event-" + id)
                .groupId(groupId)
                .locationId(locationId)
                .flowId(3L)
                .title(title)
                .message("메시지")
                .severity(severity)
                .triggerValue(Map.of("temperature", 29.5))
                .build();
        ReflectionTestUtils.setField(alert, "engineAlertId", id);
        ReflectionTestUtils.setField(alert, "createdAt", OffsetDateTime.now());
        return alert;
    }

    @Test
    void getEngineAlerts_성공() {
        EngineAlert alert = newAlert(1L, 5L, 42L, Severity.CRITICAL, "온도 임계치 초과");
        given(engineAlertRepository.search(eq(5L), eq(42L), eq(Severity.CRITICAL), any(), any(), eq(0), eq(20)))
                .willReturn(List.of(alert));

        List<EngineAlertResponse> result = engineAlertService.getEngineAlerts(5L, 42L, Severity.CRITICAL, null, null, 0,
                20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().engineAlertId()).isEqualTo(1L);
        assertThat(result.getFirst().severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void getEngineAlerts_groupId가_null이면_예외() {
        assertThatThrownBy(() -> engineAlertService.getEngineAlerts(null, 42L, null, null, null, 0, 20))
                .isInstanceOf(InvalidRequestException.class);

        verify(engineAlertRepository, never()).search(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void countEngineAlerts_성공() {
        given(engineAlertRepository.count(eq(5L), eq(42L), any(), any(), any())).willReturn(7L);

        long count = engineAlertService.countEngineAlerts(5L, 42L, null, null, null);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void countEngineAlerts_groupId가_null이면_예외() {
        assertThatThrownBy(() -> engineAlertService.countEngineAlerts(null, 42L, null, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getEngineAlert_성공() {
        EngineAlert alert = newAlert(1L, 5L, 42L, Severity.WARNING, "CO2 상승");
        given(engineAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        EngineAlertResponse result = engineAlertService.getEngineAlert(1L, 100L);

        assertThat(result.engineAlertId()).isEqualTo(1L);
        verify(groupAuthorizationService).requireMembership(5L, 100L);
    }

    @Test
    void getEngineAlert_알람이_없으면_예외() {
        given(engineAlertRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> engineAlertService.getEngineAlert(999L, 100L))
                .isInstanceOf(EngineAlertNotFoundException.class);
    }

    @Test
    void getEngineAlert_그룹_멤버가_아니면_예외() {
        EngineAlert alert = newAlert(1L, 5L, 42L, Severity.WARNING, "CO2 상승");
        given(engineAlertRepository.findById(1L)).willReturn(Optional.of(alert));
        given(groupAuthorizationService.requireMembership(5L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        assertThatThrownBy(() -> engineAlertService.getEngineAlert(1L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createEngineAlert_성공() {
        EngineAlertActionEvent event = new EngineAlertActionEvent("event-1", 5L, 42L, 3L, "제목", "메시지",
                Severity.CRITICAL, Map.of("temperature", 29.5), OffsetDateTime.now());
        given(validator.validate(event)).willReturn(Set.of());
        given(jsonMapper.writeValueAsString(event.triggerValue())).willReturn("{\"temperature\":29.5}");
        given(engineAlertRepository.claimEventId("event-1", 5L, 42L, 3L, "제목", "메시지", "CRITICAL",
                "{\"temperature\":29.5}")).willReturn(Optional.of(10L));

        engineAlertService.createEngineAlert(event);

        verify(dashboardNotificationService).create(new DashboardNotificationCreateRequest(
                5L, 42L, NotificationType.ENGINE_ALERT, 10L, "제목"));
    }

    @Test
    void createEngineAlert_이미_처리된_이벤트면_알림을_생성하지_않는다() {
        EngineAlertActionEvent event = new EngineAlertActionEvent("event-1", 5L, 42L, 3L, "제목", "메시지",
                Severity.CRITICAL, Map.of("temperature", 29.5), OffsetDateTime.now());
        given(validator.validate(event)).willReturn(Set.of());
        given(jsonMapper.writeValueAsString(event.triggerValue())).willReturn("{\"temperature\":29.5}");
        given(engineAlertRepository.claimEventId("event-1", 5L, 42L, 3L, "제목", "메시지", "CRITICAL",
                "{\"temperature\":29.5}")).willReturn(Optional.empty());

        engineAlertService.createEngineAlert(event);

        verify(dashboardNotificationService, never()).create(any());
    }

    @Test
    void createEngineAlert_검증_실패하면_예외를_던지고_저장하지_않는다() {
        EngineAlertActionEvent event = new EngineAlertActionEvent("", 5L, 42L, 3L, "", "메시지",
                Severity.CRITICAL, Map.of(), OffsetDateTime.now());
        ConstraintViolation<EngineAlertActionEvent> violation = mock(ConstraintViolation.class);
        given(validator.validate(event)).willReturn(Set.of(violation));

        assertThatThrownBy(() -> engineAlertService.createEngineAlert(event))
                .isInstanceOf(ConstraintViolationException.class);

        verify(engineAlertRepository, never()).claimEventId(any(), any(), any(), any(), any(), any(), any(), any());
        verify(dashboardNotificationService, never()).create(any());
    }

    @Test
    void deleteByGroup_성공() {
        engineAlertService.deleteByGroup(5L);

        verify(engineAlertRepository).deleteByGroupId(5L);
    }

    @Test
    void deleteByGroup_groupId가_null이면_예외() {
        assertThatThrownBy(() -> engineAlertService.deleteByGroup(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(engineAlertRepository, never()).deleteByGroupId(any());
    }

    @Test
    void deleteByLocation_성공() {
        engineAlertService.deleteByLocation(42L);

        verify(engineAlertRepository).deleteByLocationId(42L);
    }

    @Test
    void deleteByLocation_locationId가_null이면_예외() {
        assertThatThrownBy(() -> engineAlertService.deleteByLocation(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(engineAlertRepository, never()).deleteByLocationId(any());
    }

    @Test
    void summarizePeriod_심각도별_집계와_상위5개_CRITICAL_제목을_반환한다() {
        List<EngineAlert> alerts = List.of(
                newAlert(1L, 5L, 42L, Severity.CRITICAL, "알람1"),
                newAlert(2L, 5L, 42L, Severity.CRITICAL, "알람2"),
                newAlert(3L, 5L, 42L, Severity.WARNING, "알람3"),
                newAlert(4L, 5L, 42L, Severity.INFO, "알람4")
        );
        given(engineAlertRepository.searchByPeriod(eq(42L), any(), any())).willReturn(alerts);

        EngineAlertSummary summary = engineAlertService.summarizePeriod(42L, OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now());

        assertThat(summary.criticalCount()).isEqualTo(2);
        assertThat(summary.warningCount()).isEqualTo(1);
        assertThat(summary.topAlertTitles()).containsExactly("알람1", "알람2");
    }
}
