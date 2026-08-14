package com.insighton.ai.domain.enginealert.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.common.config.QuerydslConfig;
import com.insighton.ai.domain.enginealert.entity.EngineAlert;
import com.insighton.ai.domain.enginealert.entity.Severity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(QuerydslConfig.class)
class EngineAlertRepositoryTest {

    @Autowired
    private EngineAlertRepository engineAlertRepository;

    @Autowired
    private TestEntityManager entityManager;

    private int eventIdSeq = 0;

    private EngineAlert persistAlert(Long groupId, Long locationId, Severity severity, String title) {
        EngineAlert engineAlert = EngineAlert.builder()
                .eventId("event-" + (eventIdSeq++))
                .groupId(groupId)
                .locationId(locationId)
                .flowId(1L)
                .title(title)
                .message("message")
                .severity(severity)
                .build();

        return entityManager.persistFlushFind(engineAlert);
    }

    private void forceCreatedAt(EngineAlert alert, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("update engine_alerts set created_at = ?1 where engine_alert_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, alert.getEngineAlertId())
                .executeUpdate();

        entityManager.clear();
    }


    @Test
    void search_groupId로_필터링() {
        persistAlert(5L, 42L, Severity.CRITICAL, "그룹5 알람");
        persistAlert(999L, 42L, Severity.CRITICAL, "그룹999 알람");

        List<EngineAlert> result = engineAlertRepository.search(5L, null, null, null, null, 0, 20);

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("그룹5 알람");
    }

    @Test
    void search_locationId로_필터링() {
        persistAlert(5L, 42L, Severity.CRITICAL, "42층 알람");
        persistAlert(5L, 99L, Severity.CRITICAL, "99층 알람");

        List<EngineAlert> result = engineAlertRepository.search(5L, 42L, null, null, null, 0, 20);

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("42층 알람");
    }

    @Test
    void search_severity로_필터링() {
        persistAlert(5L, 42L, Severity.CRITICAL, "심각 알람");
        persistAlert(5L, 42L, Severity.WARNING, "경고 알람");

        List<EngineAlert> result = engineAlertRepository.search(5L, null, Severity.WARNING, null, null, 0, 20);

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("경고 알람");
    }

    @Test
    void search_기간으로_필터링() {
        EngineAlert old = persistAlert(5L, 42L, Severity.CRITICAL, "오래된 알람");
        EngineAlert recent = persistAlert(5L, 42L, Severity.CRITICAL, "최근 알람");
        forceCreatedAt(old, OffsetDateTime.now().minusMonths(6));
        forceCreatedAt(recent, OffsetDateTime.now());

        List<EngineAlert> result = engineAlertRepository.search(5L, null, null,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1), 0, 20);

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("최근 알람");
    }

    @Test
    void search_최신순으로_정렬() {
        EngineAlert first = persistAlert(5L, 42L, Severity.CRITICAL, "첫번째");
        EngineAlert second = persistAlert(5L, 42L, Severity.CRITICAL, "두번째");
        forceCreatedAt(first, OffsetDateTime.now().minusDays(2));
        forceCreatedAt(second, OffsetDateTime.now().minusDays(1));

        List<EngineAlert> result = engineAlertRepository.search(5L, null, null, null, null, 0, 20);

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("두번째", "첫번째");
    }

    @Test
    void search_offset_limit이_적용() {
        for (int i = 1; i <= 5; i++) {
            persistAlert(5L, 42L, Severity.CRITICAL, "알람" + i);
        }

        List<EngineAlert> result = engineAlertRepository.search(5L, null, null, null, null, 2, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void count_조건에_맞는_전체_개수_반환() {
        persistAlert(5L, 42L, Severity.CRITICAL, "알람1");
        persistAlert(5L, 42L, Severity.CRITICAL, "알람2");
        persistAlert(999L, 42L, Severity.CRITICAL, "다른 그룹");

        long count = engineAlertRepository.count(5L, null, null, null, null);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void searchByPeriod_위치와_기간으로_조회() {
        EngineAlert inRange = persistAlert(5L, 42L, Severity.CRITICAL, "기간 내");
        EngineAlert outOfRange = persistAlert(5L, 42L, Severity.CRITICAL, "기간 외");
        EngineAlert otherLocation = persistAlert(5L, 99L, Severity.CRITICAL, "다른 위치");
        forceCreatedAt(inRange, OffsetDateTime.now());
        forceCreatedAt(outOfRange, OffsetDateTime.now().minusMonths(6));
        forceCreatedAt(otherLocation, OffsetDateTime.now());

        List<EngineAlert> result = engineAlertRepository.searchByPeriod(42L,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        assertThat(result).extracting(EngineAlert::getTitle).containsExactly("기간 내");
    }

    @Test
    void deleteByGroupId_해당_그룹_알람을_전부_삭제() {
        persistAlert(5L, 42L, Severity.CRITICAL, "삭제될 알람");
        persistAlert(999L, 42L, Severity.CRITICAL, "안전한 알람");

        engineAlertRepository.deleteByGroupId(5L);
        entityManager.clear();

        assertThat(engineAlertRepository.findAll()).extracting(EngineAlert::getTitle).containsExactly("안전한 알람");
    }

    @Test
    void deleteByLocationId_해당_위치_알람을_전부_삭제() {
        persistAlert(5L, 42L, Severity.CRITICAL, "삭제될 알람");
        persistAlert(5L, 99L, Severity.CRITICAL, "안전한 알람");

        engineAlertRepository.deleteByLocationId(42L);
        entityManager.clear();

        assertThat(engineAlertRepository.findAll()).extracting(EngineAlert::getTitle).containsExactly("안전한 알람");
    }
}
