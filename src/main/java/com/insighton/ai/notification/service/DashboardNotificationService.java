package com.insighton.ai.notification.service;

import com.insighton.ai.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.notification.dto.DashboardNotificationResponse;
import java.util.List;

public interface DashboardNotificationService {

    /**
     * 그룹 ID 기준 안 읽은 알림 목록 조회 (읽은 알림은 각 원천 도메인 목록 화면에서 별도 확인).
     *
     * @param groupId 그룹 ID(필수)
     * @return 안 읽은 알림 목록 응답
     */
    List<DashboardNotificationResponse> findUnreadDashboardNotifications(Long groupId);

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
}
