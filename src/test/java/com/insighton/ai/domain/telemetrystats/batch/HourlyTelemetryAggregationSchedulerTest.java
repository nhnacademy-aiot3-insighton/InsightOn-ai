package com.insighton.ai.domain.telemetrystats.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.exceptions.InfluxException;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HourlyTelemetryAggregationSchedulerTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private QueryApi queryApi;

    @Mock
    private HourlyTelemetryStatService hourlyTelemetryStatService;

    private HourlyTelemetryAggregationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HourlyTelemetryAggregationScheduler(influxDBClient, hourlyTelemetryStatService);
        ReflectionTestUtils.setField(scheduler, "bucket", "test-bucket");
        given(influxDBClient.getQueryApi()).willReturn(queryApi);
    }

    private FluxRecord fieldNameRecord(String field) {
        FluxRecord record = mock(FluxRecord.class);
        given(record.getField()).willReturn(field);
        return record;
    }

    private FluxRecord sensorValueRecord(Long locationId, Object value) {
        FluxRecord record = mock(FluxRecord.class);
        given(record.getValueByKey("location_id")).willReturn(String.valueOf(locationId));
        given(record.getValue()).willReturn(value);
        return record;
    }

    private FluxRecord actuatorValueRecord(Long locationId, String actuatorType, double minutes) {
        FluxRecord record = mock(FluxRecord.class);
        given(record.getValueByKey("location_id")).willReturn(String.valueOf(locationId));
        given(record.getValueByKey("actuator_type")).willReturn(actuatorType);
        given(record.getValue()).willReturn(minutes);
        return record;
    }

    private FluxTable tableOf(FluxRecord... records) {
        FluxTable table = mock(FluxTable.class);
        given(table.getRecords()).willReturn(List.of(records));
        return table;
    }

    private boolean isFieldDiscoveryQuery(String flux) {
        return flux.contains("keep(columns: [\"_field\"])");
    }

    private boolean isActuatorQuery(String flux) {
        return flux.contains("integral(unit: 1m)");
    }

    @Test
    void aggregate_센서_필드가_없으면_저장할_게_없어_아무것도_생성하지_않는다() {
        given(queryApi.query(anyString())).willReturn(List.of());

        scheduler.aggregate();

        verify(hourlyTelemetryStatService, never()).create(any());
    }

    @Test
    void aggregate_정상_흐름이면_평균_최고_최저_가동시간을_저장한다() {
        given(queryApi.query(anyString())).willAnswer(invocation -> {
            String flux = invocation.getArgument(0);
            if (isFieldDiscoveryQuery(flux)) {
                return List.of(tableOf(fieldNameRecord("temperature")));
            }
            if (isActuatorQuery(flux)) {
                return List.of(tableOf(actuatorValueRecord(42L, "AIRCON", 30.0)));
            }
            if (flux.contains("|> mean()")) {
                return List.of(tableOf(sensorValueRecord(42L, 24.5)));
            }
            if (flux.contains("|> max()")) {
                return List.of(tableOf(sensorValueRecord(42L, 27.0)));
            }
            if (flux.contains("|> min()")) {
                return List.of(tableOf(sensorValueRecord(42L, 20.0)));
            }
            return List.of();
        });
        given(hourlyTelemetryStatService.findByLocationAndLogHour(eq(42L), any())).willReturn(Optional.empty());

        scheduler.aggregate();

        ArgumentCaptor<HourlyTelemetryStatCreateRequest> captor =
                ArgumentCaptor.forClass(HourlyTelemetryStatCreateRequest.class);
        verify(hourlyTelemetryStatService, times(3)).create(captor.capture());
        HourlyTelemetryStatCreateRequest request = captor.getAllValues().get(0);
        assertThat(request.locationId()).isEqualTo(42L);
        assertThat(request.metricsAvg()).contains("\"temperature\":24.5");
        assertThat(request.metricsMax()).contains("\"temperature\":27.0");
        assertThat(request.metricsMin()).contains("\"temperature\":20.0");
        assertThat(request.actuatorOnMinutes()).contains("\"AIRCON\":30.0");
    }

    @Test
    void aggregate_이미_집계된_시간창은_스킵한다() {
        given(queryApi.query(anyString())).willAnswer(invocation -> {
            String flux = invocation.getArgument(0);
            if (isFieldDiscoveryQuery(flux)) {
                return List.of(tableOf(fieldNameRecord("temperature")));
            }
            if (flux.contains("|> mean()") || flux.contains("|> max()") || flux.contains("|> min()")) {
                return List.of(tableOf(sensorValueRecord(42L, 24.0)));
            }
            return List.of();
        });
        given(hourlyTelemetryStatService.findByLocationAndLogHour(eq(42L), any()))
                .willReturn(Optional.of(mock(com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse.class)));

        scheduler.aggregate();

        verify(hourlyTelemetryStatService, never()).create(any());
    }

    @Test
    void aggregate_숫자가_아닌_필드는_건너뛰고_나머지_필드는_계속_집계된다() {
        given(queryApi.query(anyString())).willAnswer(invocation -> {
            String flux = invocation.getArgument(0);
            if (isFieldDiscoveryQuery(flux)) {
                return List.of(tableOf(fieldNameRecord("temperature"), fieldNameRecord("magnetStatus")));
            }
            boolean isMagnetStatus = flux.contains("r._field == \"magnetStatus\"");
            if (flux.contains("|> mean()")) {
                if (isMagnetStatus) {
                    throw new InfluxException("unsupported aggregate column type string");
                }
                return List.of(tableOf(sensorValueRecord(42L, 24.5)));
            }
            if (flux.contains("|> max()") || flux.contains("|> min()")) {
                if (isMagnetStatus) {
                    return List.of(tableOf(sensorValueRecord(42L, "OPEN")));
                }
                return List.of(tableOf(sensorValueRecord(42L, 25.0)));
            }
            return List.of();
        });
        given(hourlyTelemetryStatService.findByLocationAndLogHour(eq(42L), any())).willReturn(Optional.empty());

        assertThatCode(() -> scheduler.aggregate()).doesNotThrowAnyException();

        ArgumentCaptor<HourlyTelemetryStatCreateRequest> captor =
                ArgumentCaptor.forClass(HourlyTelemetryStatCreateRequest.class);
        verify(hourlyTelemetryStatService, times(3)).create(captor.capture());
        HourlyTelemetryStatCreateRequest request = captor.getAllValues().get(0);
        assertThat(request.metricsAvg()).contains("temperature").doesNotContain("magnetStatus");
    }

    @Test
    void aggregate_위치_하나가_저장에_실패해도_나머지_위치는_계속_처리된다() {
        given(queryApi.query(anyString())).willAnswer(invocation -> {
            String flux = invocation.getArgument(0);
            if (isFieldDiscoveryQuery(flux)) {
                return List.of(tableOf(fieldNameRecord("temperature")));
            }
            if (flux.contains("|> mean()") || flux.contains("|> max()") || flux.contains("|> min()")) {
                return List.of(tableOf(sensorValueRecord(42L, 24.0), sensorValueRecord(99L, 26.0)));
            }
            return List.of();
        });
        given(hourlyTelemetryStatService.findByLocationAndLogHour(any(), any())).willReturn(Optional.empty());
        given(hourlyTelemetryStatService.create(argThat(request -> request != null && request.locationId() == 42L)))
                .willThrow(new RuntimeException("저장 실패"));

        assertThatCode(() -> scheduler.aggregate()).doesNotThrowAnyException();

        verify(hourlyTelemetryStatService, times(3))
                .create(argThat(request -> request != null && request.locationId() == 99L));
    }

    @Test
    void aggregate_InfluxDB_전체_장애여도_배치_전체가_죽지_않는다() {
        given(queryApi.query(anyString())).willThrow(new InfluxException("connection refused"));

        assertThatCode(() -> scheduler.aggregate()).doesNotThrowAnyException();

        verify(hourlyTelemetryStatService, never()).create(any());
    }

    @Test
    void aggregate_액추에이터_이력이_없으면_actuatorOnMinutes는_null로_저장된다() {
        given(queryApi.query(anyString())).willAnswer(invocation -> {
            String flux = invocation.getArgument(0);
            if (isFieldDiscoveryQuery(flux)) {
                return List.of(tableOf(fieldNameRecord("temperature")));
            }
            if (flux.contains("|> mean()") || flux.contains("|> max()") || flux.contains("|> min()")) {
                return List.of(tableOf(sensorValueRecord(42L, 24.0)));
            }
            return List.of();
        });
        given(hourlyTelemetryStatService.findByLocationAndLogHour(eq(42L), any())).willReturn(Optional.empty());

        scheduler.aggregate();

        ArgumentCaptor<HourlyTelemetryStatCreateRequest> captor =
                ArgumentCaptor.forClass(HourlyTelemetryStatCreateRequest.class);
        verify(hourlyTelemetryStatService, times(3)).create(captor.capture());
        assertThat(captor.getAllValues().get(0).actuatorOnMinutes()).isNull();
    }
}
