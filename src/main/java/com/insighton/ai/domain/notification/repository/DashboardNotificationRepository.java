package com.insighton.ai.domain.notification.repository;

import com.insighton.ai.domain.notification.entity.DashboardNotification;
import com.insighton.ai.domain.notification.entity.NotificationType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardNotificationRepository extends JpaRepository<DashboardNotification, Long> {

    List<DashboardNotification> findByGroupIdAndIsReadFalseOrderByCreatedAtDesc(Long groupId);

    @Query(value = """
            select d from DashboardNotification d
            where d.groupId = :groupId
            and (:isRead is null or d.isRead = :isRead)
            and (:notificationType is null or d.notificationType = :notificationType)
            order by d.createdAt desc
            """,
            countQuery = """
                    select count(d) from DashboardNotification d
                    where d.groupId = :groupId
                    and (:isRead is null or d.isRead = :isRead)
                    and (:notificationType is null or d.notificationType = :notificationType)
                    """)
    Page<DashboardNotification> search(@Param("groupId") Long groupId,
                                       @Param("isRead") Boolean isRead,
                                       @Param("notificationType") NotificationType notificationType,
                                       Pageable pageable);

    @Modifying
    @Query("delete from DashboardNotification d where d.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from DashboardNotification d where d.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);

    @Modifying
    @Query("""
            update DashboardNotification d
            set d.isRead = true
            where d.groupId = :groupId
            and d.isRead = false
            """)
    int markAllAsRead(@Param("groupId") Long groupId);
}
