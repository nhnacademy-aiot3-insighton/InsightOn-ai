package com.insighton.ai.domain.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.dto.GroupRole;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.notification.dto.DashboardNotificationBroadcastEvent;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.DashboardNotification;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.exception.DashboardNotificationNotFoundException;
import com.insighton.ai.domain.notification.repository.DashboardNotificationRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardNotificationServiceImplTest {

    @Mock
    private DashboardNotificationRepository notificationRepository;
    @Mock
    private Validator validator;
    @Mock
    private GroupAuthorizationService groupAuthorizationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DashboardNotificationServiceImpl dashboardNotificationService;

    private DashboardNotification newNotification(Long id, Long groupId, Long locationId,
                                                  NotificationType type, Long sourceId, String title) {
        DashboardNotification notification = DashboardNotification.builder()
                .groupId(groupId)
                .locationId(locationId)
                .notificationType(type)
                .sourceId(sourceId)
                .title(title)
                .build();
        ReflectionTestUtils.setField(notification, "dashboardNotificationId", id);
        ReflectionTestUtils.setField(notification, "createdAt", OffsetDateTime.now());
        return notification;
    }

    @Test
    void findUnreadDashboardNotifications_성공() {
        DashboardNotification notification = newNotification(1L, 5L, 42L, NotificationType.REPORT, 1L, "제목");
        given(notificationRepository.findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(5L))
                .willReturn(List.of(notification));

        List<DashboardNotificationResponse> result =
                dashboardNotificationService.findUnreadDashboardNotifications(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dashboardNotificationId()).isEqualTo(1L);
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void findUnreadDashboardNotifications_groupId가_null이면_예외() {
        assertThatThrownBy(() -> dashboardNotificationService.findUnreadDashboardNotifications(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(notificationRepository, never()).findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void getDashboardNotifications_성공() {
        DashboardNotification notification = newNotification(1L, 5L, 42L, NotificationType.GATEWAY, 1L, "제목");
        Pageable pageable = PageRequest.of(0, 20);
        given(notificationRepository.search(5L, false, NotificationType.GATEWAY, pageable))
                .willReturn(new PageImpl<>(List.of(notification), pageable, 1));

        Page<DashboardNotificationResponse> result =
                dashboardNotificationService.getDashboardNotifications(5L, false, NotificationType.GATEWAY, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).dashboardNotificationId()).isEqualTo(1L);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getDashboardNotifications_필터_없으면_전체_조회() {
        DashboardNotification notification = newNotification(1L, 5L, 42L, NotificationType.REPORT, 1L, "제목");
        Pageable pageable = PageRequest.of(0, 20);
        given(notificationRepository.search(5L, null, null, pageable))
                .willReturn(new PageImpl<>(List.of(notification), pageable, 1));

        Page<DashboardNotificationResponse> result =
                dashboardNotificationService.getDashboardNotifications(5L, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getDashboardNotifications_groupId가_null이면_예외() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> dashboardNotificationService.getDashboardNotifications(null, null, null, pageable))
                .isInstanceOf(InvalidRequestException.class);

        verify(notificationRepository, never()).search(any(), any(), any(), any());
    }

    @Test
    void create_성공() {
        DashboardNotificationCreateRequest request =
                new DashboardNotificationCreateRequest(5L, 42L, NotificationType.REPORT, 1L, "제목");
        given(validator.validate(request)).willReturn(Set.of());
        given(notificationRepository.save(any(DashboardNotification.class))).willAnswer(invocation -> {
            DashboardNotification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "dashboardNotificationId", 10L);
            ReflectionTestUtils.setField(saved, "createdAt", OffsetDateTime.now());
            return saved;
        });

        DashboardNotificationResponse result = dashboardNotificationService.create(request);

        assertThat(result.dashboardNotificationId()).isEqualTo(10L);
        assertThat(result.isRead()).isFalse();

        ArgumentCaptor<DashboardNotificationBroadcastEvent> captor =
                ArgumentCaptor.forClass(DashboardNotificationBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().groupId()).isEqualTo(5L);
        assertThat(captor.getValue().notification().dashboardNotificationId()).isEqualTo(10L);
    }

    @Test
    void create_검증_실패하면_예외를_던지고_저장하지_않는다() {
        DashboardNotificationCreateRequest request =
                new DashboardNotificationCreateRequest(null, 42L, NotificationType.REPORT, 1L, "");
        ConstraintViolation<DashboardNotificationCreateRequest> violation = mock(ConstraintViolation.class);
        given(validator.validate(request)).willReturn(Set.of(violation));

        assertThatThrownBy(() -> dashboardNotificationService.create(request))
                .isInstanceOf(ConstraintViolationException.class);

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markAsRead_성공() {
        DashboardNotification notification = newNotification(1L, 5L, 42L, NotificationType.REPORT, 1L, "제목");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        DashboardNotificationResponse result = dashboardNotificationService.markAsRead(1L, 100L);

        assertThat(result.isRead()).isTrue();
        verify(groupAuthorizationService).requireRole(5L, 100L, GroupRole.MANAGER);
    }

    @Test
    void markAsRead_알림이_없으면_예외() {
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardNotificationService.markAsRead(999L, 100L))
                .isInstanceOf(DashboardNotificationNotFoundException.class);
    }

    @Test
    void markAsRead_권한이_부족하면_예외() {
        DashboardNotification notification = newNotification(1L, 5L, 42L, NotificationType.REPORT, 1L, "제목");
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));
        willThrow(new ForbiddenException("권한이 부족합니다."))
                .given(groupAuthorizationService)
                .requireRole(5L, 100L, GroupRole.MANAGER);

        assertThatThrownBy(() -> dashboardNotificationService.markAsRead(1L, 100L))
                .isInstanceOf(ForbiddenException.class);

        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void deleteByGroup_성공() {
        dashboardNotificationService.deleteByGroup(5L);

        verify(notificationRepository).deleteByGroupId(5L);
    }

    @Test
    void deleteByGroup_groupId가_null이면_예외() {
        assertThatThrownBy(() -> dashboardNotificationService.deleteByGroup(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(notificationRepository, never()).deleteByGroupId(any());
    }

    @Test
    void deleteByLocation_성공() {
        dashboardNotificationService.deleteByLocation(42L);

        verify(notificationRepository).deleteByLocationId(42L);
    }

    @Test
    void deleteByLocation_locationId가_null이면_예외() {
        assertThatThrownBy(() -> dashboardNotificationService.deleteByLocation(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(notificationRepository, never()).deleteByLocationId(any());
    }
}