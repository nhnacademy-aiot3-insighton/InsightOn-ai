package com.insighton.ai.domain.telemetrystats.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.common.config.QuerydslConfig;
import com.insighton.ai.domain.telemetrystats.entity.HourlyTelemetryStat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class HourlyTelemetryStatRepositoryTest {

    @Autowired
    private HourlyTelemetryStatRepository hourlyTelemetryStatRepository;

    @Autowired
    private TestEntityManager entityManager;

    private HourlyTelemetryStat persistStat(Long locationId, OffsetDateTime logHour) {
        HourlyTelemetryStat stat = HourlyTelemetryStat.builder()
                .locationId(locationId)
                .logHour(logHour)
                .metricsAvg("{}")
                .metricsMax("{}")
                .metricsMin("{}")
                .build();

        return entityManager.persistFlushFind(stat);
    }

    @Test
    void search_locationId로_필터링() {
        persistStat(42L, OffsetDateTime.now().withHour(10));
        persistStat(99L, OffsetDateTime.now().withHour(10));

        List<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.search(42L, null, null, Pageable.unpaged());

        assertThat(result).extracting(HourlyTelemetryStat::getLocationId).containsExactly(42L);
    }

    @Test
    void search_기간으로_필터링() {
        persistStat(42L, OffsetDateTime.now().minusMonths(6));
        HourlyTelemetryStat recent = persistStat(42L, OffsetDateTime.now());

        List<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.search(42L,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1), Pageable.unpaged());

        assertThat(result).extracting(HourlyTelemetryStat::getHourlyTelemetryStatId)
                .containsExactly(recent.getHourlyTelemetryStatId());
    }

    @Test
    void search_최신순으로_정렬한다() {
        HourlyTelemetryStat older = persistStat(42L, OffsetDateTime.now().minusHours(2));
        HourlyTelemetryStat newer = persistStat(42L, OffsetDateTime.now().minusHours(1));

        List<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.search(42L, null, null, Pageable.unpaged());

        assertThat(result).extracting(HourlyTelemetryStat::getHourlyTelemetryStatId)
                .containsExactly(newer.getHourlyTelemetryStatId(), older.getHourlyTelemetryStatId());
    }

    @Test
    void search_페이징이_적용된다() {
        for (int i = 0; i < 5; i++) {
            persistStat(42L, OffsetDateTime.now().minusHours(i));
        }

        List<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.search(42L, null, null,
                Pageable.ofSize(2).withPage(1));

        assertThat(result).hasSize(2);
    }

    @Test
    void count_조건에_맞는_전체_개수_반환() {
        persistStat(42L, OffsetDateTime.now().minusHours(1));
        persistStat(42L, OffsetDateTime.now());
        persistStat(99L, OffsetDateTime.now());

        long count = hourlyTelemetryStatRepository.count(42L, null, null);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findByLocationIdAndLogHour_존재하면_반환한다() {
        OffsetDateTime hour = OffsetDateTime.now().withMinute(0).withSecond(0).withNano(0);
        persistStat(42L, hour);

        Optional<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.findByLocationIdAndLogHour(42L, hour);

        assertThat(result).isPresent();
    }

    @Test
    void findByLocationIdAndLogHour_없으면_빈_Optional_반환한다() {
        Optional<HourlyTelemetryStat> result = hourlyTelemetryStatRepository.findByLocationIdAndLogHour(42L,
                OffsetDateTime.now());

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByLocationId_해당_위치_통계를_전부_삭제한다() {
        persistStat(42L, OffsetDateTime.now());
        persistStat(99L, OffsetDateTime.now());

        hourlyTelemetryStatRepository.deleteByLocationId(42L);
        entityManager.clear();

        assertThat(hourlyTelemetryStatRepository.findAll()).extracting(HourlyTelemetryStat::getLocationId)
                .containsExactly(99L);
    }

    @Test
    void deleteByLocationIdIn_여러_위치_통계를_전부_삭제한다() {
        persistStat(42L, OffsetDateTime.now());
        persistStat(99L, OffsetDateTime.now());
        persistStat(7L, OffsetDateTime.now());

        hourlyTelemetryStatRepository.deleteByLocationIdIn(List.of(42L, 99L));
        entityManager.clear();

        assertThat(hourlyTelemetryStatRepository.findAll()).extracting(HourlyTelemetryStat::getLocationId)
                .containsExactly(7L);
    }

    @Test
    void findDistinctLocationIds_기간_내_위치_목록을_반환한다() {
        persistStat(42L, OffsetDateTime.now());
        persistStat(42L, OffsetDateTime.now().minusHours(1));
        persistStat(99L, OffsetDateTime.now());
        persistStat(7L, OffsetDateTime.now().minusMonths(6));

        List<Long> result = hourlyTelemetryStatRepository.findDistinctLocationIds(
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        assertThat(result).containsExactlyInAnyOrder(42L, 99L);
    }
}