package com.insighton.ai.domain.notification.repository;

import com.insighton.ai.domain.notification.entity.DashboardNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardNotificationRepository extends JpaRepository<DashboardNotification, Long> {

    List<DashboardNotification> findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(Long groupId);

    @Modifying
    @Query("delete from DashboardNotification d where d.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from DashboardNotification d where d.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);
}
