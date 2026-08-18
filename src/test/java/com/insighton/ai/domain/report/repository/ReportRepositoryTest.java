package com.insighton.ai.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.common.config.QuerydslConfig;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Report persistReport(Long groupId, Long locationId,
                                 ReportType reportType, String title) {
        Report report = Report.builder()
                .groupId(groupId)
                .locationId(locationId)
                .title(title)
                .reportType(reportType)
                .content("본문")
                .build();

        return entityManager.persistFlushFind(report);
    }

    private void forceCreatedAt(Report report, OffsetDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("update reports set created_at = ?1 where report_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, report.getReportId())
                .executeUpdate();

        entityManager.clear();
    }

    @Test
    void search_groupId로_필터링() {
        persistReport(5L, 42L, ReportType.WEEKLY, "그룹5 리포트");
        persistReport(999L, 42L, ReportType.WEEKLY, "그룹999 리포트");

        List<Report> result = reportRepository.search(5L, null, null, null, null, 0, 20);

        assertThat(result).extracting(Report::getTitle).contains("그룹5 리포트");
    }

    @Test
    void search_locationId로_필터링() {
        persistReport(5L, 42L, ReportType.WEEKLY, "42층 리포트");
        persistReport(5L, 99L, ReportType.WEEKLY, "99층 리포트");

        List<Report> result = reportRepository.search(5L, 42L, null, null, null, 0, 20);

        assertThat(result).extracting(Report::getTitle).containsExactly("42층 리포트");
    }

    @Test
    void search_reportType으로_필터링() {
        persistReport(5L, 42L, ReportType.WEEKLY, "주간 리포트");
        persistReport(5L, 42L, ReportType.MONTHLY, "월간 리포트");

        List<Report> result = reportRepository.search(5L, null, ReportType.MONTHLY, null, null, 0, 20);

        assertThat(result).extracting(Report::getTitle).containsExactly("월간 리포트");
    }

    @Test
    void search_기간으로_필터링한다() {
        Report old = persistReport(5L, 42L, ReportType.WEEKLY, "오래된 리포트");
        Report recent = persistReport(5L, 42L, ReportType.WEEKLY, "최근 리포트");
        forceCreatedAt(old, OffsetDateTime.now().minusMonths(6));
        forceCreatedAt(recent, OffsetDateTime.now());

        List<Report> result = reportRepository.search(5L, null, null,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1), 0, 20);

        assertThat(result).extracting(Report::getTitle).containsExactly("최근 리포트");
    }

    @Test
    void search_최신순으로_정렬한다() {
        Report first = persistReport(5L, 42L, ReportType.WEEKLY, "첫번째");
        Report second = persistReport(5L, 42L, ReportType.WEEKLY, "두번째");
        forceCreatedAt(first, OffsetDateTime.now().minusDays(2));
        forceCreatedAt(second, OffsetDateTime.now().minusDays(1));

        List<Report> result = reportRepository.search(5L, null, null, null, null, 0, 20);

        assertThat(result).extracting(Report::getTitle).containsExactly("두번째", "첫번째");
    }

    @Test
    void search_offset_limit이_적용된다() {
        for (int i = 1; i <= 5; i++) {
            persistReport(5L, 42L, ReportType.WEEKLY, "리포트" + i);
        }

        List<Report> result = reportRepository.search(5L, null, null, null, null, 2, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void count_조건에_맞는_전체_개수_반환() {
        persistReport(5L, 42L, ReportType.WEEKLY, "리포트1");
        persistReport(5L, 42L, ReportType.WEEKLY, "리포트2");
        persistReport(999L, 42L, ReportType.WEEKLY, "다른 그룹");

        long count = reportRepository.count(5L, null, null, null, null);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void deleteByGroupId_해당_그룹_리포트를_전부_삭제한다() {
        persistReport(5L, 42L, ReportType.WEEKLY, "삭제될 리포트");
        persistReport(999L, 42L, ReportType.WEEKLY, "안전한 리포트");

        reportRepository.deleteByGroupId(5L);
        entityManager.clear();

        assertThat(reportRepository.findAll()).extracting(Report::getTitle).containsExactly("안전한 리포트");
    }

    @Test
    void deleteByLocationId_해당_위치_리포트를_전부_삭제한다() {
        persistReport(5L, 42L, ReportType.WEEKLY, "삭제될 리포트");
        persistReport(5L, 99L, ReportType.WEEKLY, "안전한 리포트");

        reportRepository.deleteByLocationId(42L);
        entityManager.clear();

        assertThat(reportRepository.findAll()).extracting(Report::getTitle).containsExactly("안전한 리포트");
    }
}
