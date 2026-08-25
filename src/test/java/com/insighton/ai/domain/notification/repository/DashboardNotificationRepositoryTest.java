package com.insighton.ai.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.common.config.QuerydslConfig;
import com.insighton.ai.domain.notification.entity.DashboardNotification;
import com.insighton.ai.domain.notification.entity.NotificationType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class DashboardNotificationRepositoryTest {

    @Autowired
    private DashboardNotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private DashboardNotification persistNotification(Long groupId, NotificationType type, String title,
                                                       boolean read) {
        DashboardNotification notification = DashboardNotification.builder()
                .groupId(groupId)
                .locationId(42L)
                .notificationType(type)
                .sourceId(1L)
                .title(title)
                .build();
        if (read) {
            notification.markAsRead();
        }
        return entityManager.persistFlushFind(notification);
    }

    private void forceCreatedAt(DashboardNotification notification, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("update dashboard_notifications set created_at = ?1 where dashboard_notification_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, notification.getDashboardNotificationId())
                .executeUpdate();

        entityManager.clear();
    }

    @Test
    void search_groupId로_필터링() {
        persistNotification(5L, NotificationType.REPORT, "그룹5 알림", false);
        persistNotification(999L, NotificationType.REPORT, "그룹999 알림", false);

        Page<DashboardNotification> result = notificationRepository.search(5L, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DashboardNotification::getTitle).containsExactly("그룹5 알림");
    }

    @Test
    void search_isRead가_false면_안읽은_알림만_반환() {
        persistNotification(5L, NotificationType.REPORT, "안읽음", false);
        persistNotification(5L, NotificationType.REPORT, "읽음", true);

        Page<DashboardNotification> result = notificationRepository.search(5L, false, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DashboardNotification::getTitle).containsExactly("안읽음");
    }

    @Test
    void search_isRead가_true면_읽은_알림만_반환() {
        persistNotification(5L, NotificationType.REPORT, "안읽음", false);
        persistNotification(5L, NotificationType.REPORT, "읽음", true);

        Page<DashboardNotification> result = notificationRepository.search(5L, true, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DashboardNotification::getTitle).containsExactly("읽음");
    }

    @Test
    void search_isRead가_null이면_전체_반환() {
        persistNotification(5L, NotificationType.REPORT, "안읽음", false);
        persistNotification(5L, NotificationType.REPORT, "읽음", true);

        Page<DashboardNotification> result = notificationRepository.search(5L, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void search_notificationType으로_필터링() {
        persistNotification(5L, NotificationType.REPORT, "리포트 알림", false);
        persistNotification(5L, NotificationType.GATEWAY, "게이트웨이 알림", false);

        Page<DashboardNotification> result =
                notificationRepository.search(5L, null, NotificationType.GATEWAY, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DashboardNotification::getTitle).containsExactly("게이트웨이 알림");
    }

    @Test
    void search_최신순으로_정렬한다() {
        DashboardNotification first = persistNotification(5L, NotificationType.REPORT, "첫번째", false);
        DashboardNotification second = persistNotification(5L, NotificationType.REPORT, "두번째", false);
        forceCreatedAt(first, OffsetDateTime.now().minusDays(2));
        forceCreatedAt(second, OffsetDateTime.now().minusDays(1));

        Page<DashboardNotification> result = notificationRepository.search(5L, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(DashboardNotification::getTitle)
                .containsExactly("두번째", "첫번째");
    }

    @Test
    void search_페이지네이션이_적용되고_전체_개수를_정확히_센다() {
        for (int i = 1; i <= 5; i++) {
            persistNotification(5L, NotificationType.REPORT, "알림" + i, false);
        }

        Page<DashboardNotification> result = notificationRepository.search(5L, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }
}
