package com.insighton.ai.domain.telemetrystats.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.domain.telemetrystats.dto.PeriodTelemetrySummary;
import com.insighton.ai.domain.telemetrystats.entity.HourlyTelemetryStat;
import com.insighton.ai.domain.telemetrystats.repository.HourlyTelemetryStatRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class HourlyTelemetryStatServiceImplTest {

    @Mock
    private HourlyTelemetryStatRepository hourlyTelemetryStatRepository;
    @Mock
    private Validator validator;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private HourlyTelemetryStatServiceImpl hourlyTelemetryStatService;

    private HourlyTelemetryStat newStat(Long id, Long locationId, OffsetDateTime logHour, String metricsAvg,
                                        String metricsMax, String metricsMin, String actuatorOnMinutes) {
        HourlyTelemetryStat stat = HourlyTelemetryStat.builder()
                .locationId(locationId)
                .logHour(logHour)
                .metricsAvg(metricsAvg)
                .metricsMax(metricsMax)
                .metricsMin(metricsMin)
                .actuatorOnMinutes(actuatorOnMinutes)
                .build();
        ReflectionTestUtils.setField(stat, "hourlyTelemetryStatId", id);
        ReflectionTestUtils.setField(stat, "createdAt", OffsetDateTime.now());
        return stat;
    }

    @Test
    void findHourlyTelemetryStats_성공() {
        given(coreClient.getLocation(42L)).willReturn(
                new LocationResponse(42L, "2층 사무실", 5L, AutoControlMode.SUGGESTION));
        HourlyTelemetryStat stat = newStat(1L, 42L, OffsetDateTime.now(), "{}", "{}", "{}", null);
        given(hourlyTelemetryStatRepository.search(eq(42L), any(), any(), any())).willReturn(List.of(stat));

        List<HourlyTelemetryStatResponse> result =
                hourlyTelemetryStatService.findHourlyTelemetryStats(5L, 42L, null, null, Pageable.unpaged());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hourlyTelemetryStatId()).isEqualTo(1L);
    }

    @Test
    void findHourlyTelemetryStats_groupId가_null이면_예외() {
        assertThatThrownBy(() ->
                hourlyTelemetryStatService.findHourlyTelemetryStats(null, 42L, null, null, Pageable.unpaged()))
                .isInstanceOf(InvalidRequestException.class);

        verify(coreClient, never()).getLocation(any());
    }

    @Test
    void findHourlyTelemetryStats_locationId가_null이면_예외() {
        assertThatThrownBy(() ->
                hourlyTelemetryStatService.findHourlyTelemetryStats(5L, null, null, null, Pageable.unpaged()))
                .isInstanceOf(InvalidRequestException.class);

        verify(coreClient, never()).getLocation(any());
    }

    @Test
    void findHourlyTelemetryStats_다른_그룹_소속_위치면_예외() {
        given(coreClient.getLocation(42L)).willReturn(
                new LocationResponse(42L, "2층 사무실", 999L, AutoControlMode.SUGGESTION));

        assertThatThrownBy(() ->
                hourlyTelemetryStatService.findHourlyTelemetryStats(5L, 42L, null, null, Pageable.unpaged()))
                .isInstanceOf(ForbiddenException.class);

        verify(hourlyTelemetryStatRepository, never()).search(any(), any(), any(), any());
    }

    @Test
    void countHourlyTelemetryStats_성공() {
        given(coreClient.getLocation(42L)).willReturn(
                new LocationResponse(42L, "2층 사무실", 5L, AutoControlMode.SUGGESTION));
        given(hourlyTelemetryStatRepository.count(eq(42L), any(), any())).willReturn(24L);

        long count = hourlyTelemetryStatService.countHourlyTelemetryStats(5L, 42L, null, null);

        assertThat(count).isEqualTo(24L);
    }

    @Test
    void create_성공() {
        HourlyTelemetryStatCreateRequest request = new HourlyTelemetryStatCreateRequest(42L, OffsetDateTime.now(),
                "{\"temperature\":24.0}", "{\"temperature\":26.0}", "{\"temperature\":22.0}", null);
        given(validator.validate(request)).willReturn(Set.of());
        given(hourlyTelemetryStatRepository.save(any(HourlyTelemetryStat.class))).willAnswer(invocation -> {
            HourlyTelemetryStat saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "hourlyTelemetryStatId", 10L);
            return saved;
        });

        HourlyTelemetryStatResponse result = hourlyTelemetryStatService.create(request);

        assertThat(result.hourlyTelemetryStatId()).isEqualTo(10L);
        assertThat(result.locationId()).isEqualTo(42L);
    }

    @Test
    void create_검증_실패하면_예외를_던지고_저장하지_않는다() {
        HourlyTelemetryStatCreateRequest request = new HourlyTelemetryStatCreateRequest(null, null, "", "", "", null);
        ConstraintViolation<HourlyTelemetryStatCreateRequest> violation = mock(ConstraintViolation.class);
        given(validator.validate(request)).willReturn(Set.of(violation));

        assertThatThrownBy(() -> hourlyTelemetryStatService.create(request))
                .isInstanceOf(ConstraintViolationException.class);

        verify(hourlyTelemetryStatRepository, never()).save(any());
    }

    @Test
    void findByLocationAndLogHour_성공() {
        OffsetDateTime logHour = OffsetDateTime.now();
        HourlyTelemetryStat stat = newStat(1L, 42L, logHour, "{}", "{}", "{}", null);
        given(hourlyTelemetryStatRepository.findByLocationIdAndLogHour(42L, logHour))
                .willReturn(Optional.of(stat));

        Optional<HourlyTelemetryStatResponse> result =
                hourlyTelemetryStatService.findByLocationAndLogHour(42L, logHour);

        assertThat(result).isPresent();
        assertThat(result.get().hourlyTelemetryStatId()).isEqualTo(1L);
    }

    @Test
    void findByLocationAndLogHour_없으면_빈값() {
        OffsetDateTime logHour = OffsetDateTime.now();
        given(hourlyTelemetryStatRepository.findByLocationIdAndLogHour(42L, logHour))
                .willReturn(Optional.empty());

        assertThat(hourlyTelemetryStatService.findByLocationAndLogHour(42L, logHour)).isEmpty();
    }

    @Test
    void findByLocationAndLogHour_locationId가_null이면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.findByLocationAndLogHour(null, OffsetDateTime.now()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void findByLocationAndLogHour_logHour가_null이면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.findByLocationAndLogHour(42L, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteByLocation_성공() {
        hourlyTelemetryStatService.deleteByLocation(42L);

        verify(hourlyTelemetryStatRepository).deleteByLocationId(42L);
    }

    @Test
    void deleteByLocation_locationId가_null이면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.deleteByLocation(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(hourlyTelemetryStatRepository, never()).deleteByLocationId(any());
    }

    @Test
    void deleteByLocations_성공() {
        hourlyTelemetryStatService.deleteByLocations(List.of(1L, 2L));

        verify(hourlyTelemetryStatRepository).deleteByLocationIdIn(List.of(1L, 2L));
    }

    @Test
    void deleteByLocations_비어있으면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.deleteByLocations(List.of()))
                .isInstanceOf(InvalidRequestException.class);

        verify(hourlyTelemetryStatRepository, never()).deleteByLocationIdIn(any());
    }

    @Test
    void deleteByLocations_null이면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.deleteByLocations(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void summarizePeriod_locationId가_null이면_예외() {
        assertThatThrownBy(() -> hourlyTelemetryStatService.summarizePeriod(null, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void summarizePeriod_평균의평균_최고값_최저값_합산_시간대버킷을_전부_계산한다() {
        OffsetDateTime hour14 = OffsetDateTime.parse("2026-08-13T14:00:00+09:00");
        OffsetDateTime hour15 = OffsetDateTime.parse("2026-08-13T15:00:00+09:00");
        HourlyTelemetryStat stat1 = newStat(1L, 42L, hour14,
                "{\"temperature\":24.0}", "{\"temperature\":26.0}", "{\"temperature\":22.0}", "{\"AIRCON\":30.0}");
        HourlyTelemetryStat stat2 = newStat(2L, 42L, hour15,
                "{\"temperature\":28.0}", "{\"temperature\":30.0}", "{\"temperature\":25.0}", "{\"AIRCON\":45.0}");

        given(hourlyTelemetryStatRepository.search(eq(42L), any(), any(), eq(Pageable.unpaged())))
                .willReturn(List.of(stat1, stat2));

        given(jsonMapper.readValue(eq("{\"temperature\":24.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 24.0));
        given(jsonMapper.readValue(eq("{\"temperature\":26.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 26.0));
        given(jsonMapper.readValue(eq("{\"temperature\":22.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 22.0));
        given(jsonMapper.readValue(eq("{\"AIRCON\":30.0}"), any(TypeReference.class)))
                .willReturn(Map.of("AIRCON", 30.0));
        given(jsonMapper.readValue(eq("{\"temperature\":28.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 28.0));
        given(jsonMapper.readValue(eq("{\"temperature\":30.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 30.0));
        given(jsonMapper.readValue(eq("{\"temperature\":25.0}"), any(TypeReference.class)))
                .willReturn(Map.of("temperature", 25.0));
        given(jsonMapper.readValue(eq("{\"AIRCON\":45.0}"), any(TypeReference.class)))
                .willReturn(Map.of("AIRCON", 45.0));

        PeriodTelemetrySummary summary =
                hourlyTelemetryStatService.summarizePeriod(42L, hour14, hour15);

        assertThat(summary.metricsAvg().get("temperature")).isEqualTo(26.0);
        assertThat(summary.metricsMax().get("temperature")).isEqualTo(30.0);
        assertThat(summary.metricsMin().get("temperature")).isEqualTo(22.0);
        assertThat(summary.actuatorOnMinutes().get("AIRCON")).isEqualTo(75.0);
        assertThat(summary.hourlyAvgByMetric().get("temperature")).containsEntry(14, 24.0).containsEntry(15, 28.0);
    }

    @Test
    void summarizePeriod_JSON_파싱_실패하면_예외() {
        HourlyTelemetryStat stat = newStat(1L, 42L, OffsetDateTime.now(), "잘못된 JSON", "{}", "{}", null);
        given(hourlyTelemetryStatRepository.search(eq(42L), any(), any(), eq(Pageable.unpaged())))
                .willReturn(List.of(stat));
        given(jsonMapper.readValue(anyString(), any(TypeReference.class)))
                .willThrow(new RuntimeException("파싱 실패"));

        assertThatThrownBy(() -> hourlyTelemetryStatService.summarizePeriod(42L, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findDistinctLocationIds_repository에_위임한다() {
        given(hourlyTelemetryStatRepository.findDistinctLocationIds(any(), any())).willReturn(List.of(1L, 2L));

        List<Long> result = hourlyTelemetryStatService.findDistinctLocationIds(null, null);

        assertThat(result).containsExactly(1L, 2L);
    }
}