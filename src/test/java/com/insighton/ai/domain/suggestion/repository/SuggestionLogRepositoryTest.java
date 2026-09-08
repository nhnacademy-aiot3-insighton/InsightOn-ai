package com.insighton.ai.domain.suggestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.common.config.QuerydslConfig;
import com.insighton.ai.domain.suggestion.entity.SuggestionLog;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(QuerydslConfig.class)
class SuggestionLogRepositoryTest {

    @Autowired
    private SuggestionLogRepository suggestionLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SuggestionLog persistSuggestion(Long groupId, Long locationId, String title) {
        SuggestionLog suggestionLog = SuggestionLog.builder()
                .groupId(groupId)
                .locationId(locationId)
                .title(title)
                .suggestionText("제안 내용")
                .actionPayload("{}")
                .build();

        return entityManager.persistFlushFind(suggestionLog);
    }

    private void forceCreatedAt(SuggestionLog suggestionLog, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("update suggestion_logs set created_at = ?1 where suggestion_log_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, suggestionLog.getSuggestionLogId())
                .executeUpdate();

        entityManager.clear();
    }

    @Test
    void search_locationId로_필터링() {
        persistSuggestion(5L, 42L, "42층 제안");
        persistSuggestion(5L, 99L, "99층 제안");

        List<SuggestionLog> result = suggestionLogRepository.search(5L, 42L, null, null, Pageable.unpaged());

        assertThat(result).extracting(SuggestionLog::getTitle).containsExactly("42층 제안");
    }

    @Test
    void search_locationId가_null이면_전체_조회() {
        persistSuggestion(5L, 42L, "42층 제안");
        persistSuggestion(5L, 99L, "99층 제안");

        List<SuggestionLog> result = suggestionLogRepository.search(5L, null, null, null, Pageable.unpaged());

        assertThat(result).extracting(SuggestionLog::getTitle).containsExactlyInAnyOrder("42층 제안", "99층 제안");
    }

    @Test
    void search_기간으로_필터링() {
        SuggestionLog old = persistSuggestion(5L, 42L, "오래된 제안");
        SuggestionLog recent = persistSuggestion(5L, 42L, "최근 제안");

        forceCreatedAt(old, OffsetDateTime.now().minusMonths(6));
        forceCreatedAt(recent, OffsetDateTime.now());

        List<SuggestionLog> result = suggestionLogRepository.search(5L, null, OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(1), Pageable.unpaged());

        assertThat(result).extracting(SuggestionLog::getTitle).containsExactly("최근 제안");
    }

    @Test
    void search_최신순으로_정렬() {
        SuggestionLog first = persistSuggestion(5L, 42L, "첫번째");
        SuggestionLog second = persistSuggestion(5L, 42L, "두번째");

        forceCreatedAt(first, OffsetDateTime.now().minusDays(2));
        forceCreatedAt(second, OffsetDateTime.now().minusDays(1));

        List<SuggestionLog> result = suggestionLogRepository.search(5L, null, null, null, Pageable.unpaged());

        assertThat(result).extracting(SuggestionLog::getTitle).containsExactly("두번째", "첫번째");
    }

    @Test
    void search_페이징_적용() {
        for (int i = 1; i <= 5; i++) {
            persistSuggestion(5L, 42L, "제안" + i);
        }

        List<SuggestionLog> result = suggestionLogRepository.search(5L, null, null, null,
                Pageable.ofSize(2).withPage(1));

        assertThat(result).hasSize(2);
    }

    @Test
    void count_조건에_맞는_전체_개수_반환() {
        persistSuggestion(5L, 42L, "제안1");
        persistSuggestion(5L, 42L, "제안2");
        persistSuggestion(999L, 42L, "다른 그룹");

        long count = suggestionLogRepository.count(5L, null, null, null);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void searchByPeriod_위치와_기간으로_조회() {
        SuggestionLog inRange = persistSuggestion(5L, 42L, "기간 내");
        SuggestionLog outOfRange = persistSuggestion(5L, 42L, "기간 외");
        SuggestionLog otherLocation = persistSuggestion(5L, 99L, "다른 위치");
        forceCreatedAt(inRange, OffsetDateTime.now());
        forceCreatedAt(outOfRange, OffsetDateTime.now().minusMonths(6));
        forceCreatedAt(otherLocation, OffsetDateTime.now());

        List<SuggestionLog> result = suggestionLogRepository.searchByPeriod(42L,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        assertThat(result).extracting(SuggestionLog::getTitle).containsExactly("기간 내");
    }

    @Test
    void deleteByGroupId_해당_그룹_제안을_전부_삭제한다() {
        persistSuggestion(5L, 42L, "삭제될 제안");
        persistSuggestion(999L, 42L, "안전한 제안");

        suggestionLogRepository.deleteByGroupId(5L);
        entityManager.clear();

        assertThat(suggestionLogRepository.findAll()).extracting(SuggestionLog::getTitle).containsExactly("안전한 제안");
    }

    @Test
    void deleteByLocationId_해당_위치_제안_전부_삭제() {
        persistSuggestion(5L, 42L, "삭제될 제안");
        persistSuggestion(5L, 99L, "안전한 제안");

        suggestionLogRepository.deleteByLocationId(42L);
        entityManager.clear();

        assertThat(suggestionLogRepository.findAll()).extracting(SuggestionLog::getTitle).containsExactly("안전한 제안");
    }
}
