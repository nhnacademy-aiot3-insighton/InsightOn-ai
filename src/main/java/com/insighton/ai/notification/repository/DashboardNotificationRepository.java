package com.insighton.ai.notification.repository;

import com.insighton.ai.notification.domain.DashboardNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardNotificationRepository extends JpaRepository<DashboardNotification, Long> {

    List<DashboardNotification> findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(Long groupId);
}
