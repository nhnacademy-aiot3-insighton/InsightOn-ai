package com.insighton.ai.notification.service.impl;

import com.insighton.ai.exception.InvalidRequestException;
import com.insighton.ai.groupauth.domain.GroupRole;
import com.insighton.ai.groupauth.service.GroupAuthorizationService;
import com.insighton.ai.notification.domain.DashboardNotification;
import com.insighton.ai.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.notification.exception.DashboardNotificationNotFoundException;
import com.insighton.ai.notification.repository.DashboardNotificationRepository;
import com.insighton.ai.notification.service.DashboardNotificationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
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
public class DashboardNotificationServiceImpl implements DashboardNotificationService {

    private final DashboardNotificationRepository notificationRepository;
    private final Validator validator;
    private final GroupAuthorizationService groupAuthorizationService;

    /**
     * 그룹 ID 기준 안 읽은 알림 목록 조회.
     *
     * @param groupId 그룹 ID(필수)
     * @return 안 읽은 알림 목록 응답
     * @throws InvalidRequestException groupId가 null인 경우
     */
    @Override
    public List<DashboardNotificationResponse> findUnreadDashboardNotifications(Long groupId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }

        List<DashboardNotification> notifications = notificationRepository.findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(
                groupId);

        log.info("알림 목록 조회 - groupId:{}, size:{}",
                groupId, notifications.size());

        return notifications.stream()
                .map(DashboardNotificationResponse::from)
                .toList();
    }

    /**
     * 알림 신규 생성, 저장 전 Bean Validation 기반 요청값 유효성 검증 수행 (engine_alerts/suggestion_logs/reports 생성과 같은 트랜잭션에서 호출되는 내부용).
     *
     * @param request 알림 생성 요청
     * @return 저장된 알림 응답
     * @throws ConstraintViolationException 요청값 검증 실패 시
     */
    @Transactional
    @Override
    public DashboardNotificationResponse create(DashboardNotificationCreateRequest request) {
        Set<ConstraintViolation<DashboardNotificationCreateRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        DashboardNotification notification = DashboardNotification.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .notificationType(request.notificationType())
                .sourceId(request.sourceId())
                .title(request.title())
                .build();

        DashboardNotification saved = notificationRepository.save(notification);

        log.info("알림 생성 - dashboardNotificationId:{}, notificationType:{}, sourceId:{}",
                saved.getDashboardNotificationId(), saved.getNotificationType(), saved.getSourceId());

        return DashboardNotificationResponse.from(saved);
    }

    /**
     * 알림 읽음 처리.
     *
     * @param dashboardNotificationId 알림 ID
     * @param userId                  요청자 유저 ID
     * @return 읽음 처리된 알림 응답
     * @throws DashboardNotificationNotFoundException 해당 ID의 알림 미존재 시
     */
    @Transactional
    @Override
    public DashboardNotificationResponse markAsRead(Long dashboardNotificationId, Long userId) {

        DashboardNotification notification = notificationRepository.findById(dashboardNotificationId)
                .orElseThrow(() -> new DashboardNotificationNotFoundException(dashboardNotificationId));

        groupAuthorizationService.requireRole(notification.getGroupId(), userId, GroupRole.MANAGER);

        notification.markAsRead();

        log.info("알림 읽음 처리 - dashboardNotificationId:{}", dashboardNotificationId);

        return DashboardNotificationResponse.from(notification);
    }

    @Transactional
    @Override
    public void deleteByGroup(Long groupId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }
        notificationRepository.deleteByGroupId(groupId);
        log.info("대시보드 알람 일괄 삭제 - groupId:{}", groupId);
    }

    @Transactional
    @Override
    public void deleteByLocation(Long locationId) {
        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }
        notificationRepository.deleteByLocationId(locationId);
        log.info("대시보드 알람 일괄 삭제 - locationId:{}", locationId);
    }
}
