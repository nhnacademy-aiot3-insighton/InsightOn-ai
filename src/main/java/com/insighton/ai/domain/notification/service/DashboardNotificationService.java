package com.insighton.ai.domain.notification.service;

import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.NotificationType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DashboardNotificationService {

    /**
     * 그룹 ID 기준 안 읽은 알림 목록 조회 (읽은 알림은 각 원천 도메인 목록 화면에서 별도 확인).
     *
     * @param groupId 그룹 ID(필수)
     * @return 안 읽은 알림 목록 응답
     */
    List<DashboardNotificationResponse> findUnreadDashboardNotifications(Long groupId);

    /**
     * 그룹 ID 기준 전체 알림 목록을 읽음 여부·알림 타입으로 필터링해 조회 (알림 히스토리 화면용).
     *
     * @param groupId          그룹 ID(필수)
     * @param isRead           읽음 여부로 필터링, null이면 전체
     * @param notificationType 알림 타입으로 필터링, null이면 전체
     * @param pageable         페이지 정보
     * @return 필터링된 알림 페이지 응답
     */
    Page<DashboardNotificationResponse> getDashboardNotifications(Long groupId, Boolean isRead,
                                                                  NotificationType notificationType,
                                                                  Pageable pageable);

    /**
     * 알림 신규 생성 (engine_alerts/suggestion_logs/reports 생성과 같은 트랜잭션에서 호출되는 내부용).
     *
     * @param request 알림 생성 요청
     * @return 저장된 알림 응답
     */
    DashboardNotificationResponse create(DashboardNotificationCreateRequest request);

    /**
     * 알림 읽음 처리.
     *
     * @param dashboardNotificationId 알림 ID
     * @param userId                  요청자 유저 ID
     * @return 읽음 처리된 알림 응답
     */
    DashboardNotificationResponse markAsRead(Long dashboardNotificationId, Long userId);

    void deleteByGroup(Long groupId);

    void deleteByLocation(Long locationId);

    /**
     * 그룹 ID 기준 안 읽은 알림 전체를 읽음 처리. MANAGER 이상만 가능(개별 읽음 처리와 동일 권한).
     *
     * @param groupId 그룹 ID(필수)
     * @param userId  요청자 유저 ID
     * @return 실제로 읽음 처리된 알림 건수
     */
    int markAllAsRead(Long groupId, Long userId);
}
