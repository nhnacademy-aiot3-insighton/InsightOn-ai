package com.insighton.ai.telemetrystats.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 정각, 직전 1시간의 InfluxDB {@code sensor_data}/{@code actuator_status} 원시 데이터를 location_id 기준으로 집계해
 * hourly_telemetry_stats에 저장.
 * <p>Core locations 조회 없이, InfluxDB에 실제로 데이터가 들어온 location_id 태그만으로 집계 대상을 확보
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HourlyTelemetryAggregationScheduler {

    private final InfluxDBClient influxDBClient;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${influx.bucket}")
    private String bucket;

    /**
     * 정각 배치 진입점. 직전 1시간(예: 15시 정각 실행 시 14시~15시) 구간을 집계 윈도우로 잡고, sensor_data 기준 평균/최고/최저와 actuator_status 기준 가동 분을 각각
     * 조회한 뒤 location별로 묶어 저장한다. ShedLock이 걸려있어도, 수동 재실행 등 예외 상황을 대비해 저장 전 중복 집계 여부를 한 번 더 확인한다. location하나가 실패해도 나머지는
     * 계속 처리
     */
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "hourlyTelemetryAggregation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void aggregate() {
        OffsetDateTime windowEnd = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime windowStart = windowEnd.minusHours(1);

        Map<Long, Map<String, Double>> avgByLocation = querySensorAggregate("mean", windowStart, windowEnd);
        Map<Long, Map<String, Double>> maxByLocation = querySensorAggregate("max", windowStart, windowEnd);
        Map<Long, Map<String, Double>> minByLocation = querySensorAggregate("min", windowStart, windowEnd);
        Map<Long, Map<String, Double>> actuatorByLocation = queryActuatorMinutes(windowStart, windowEnd);

        Set<Long> locationIds = new HashSet<>();
        locationIds.addAll(avgByLocation.keySet());
        locationIds.addAll(maxByLocation.keySet());
        locationIds.addAll(minByLocation.keySet());
        locationIds.addAll(actuatorByLocation.keySet());

        for (Long locationId : locationIds) {

            //location별 예외 격리
            try {
                if (hourlyTelemetryStatService.findByLocationAndLogHour(locationId, windowStart).isPresent()) {
                    log.info("시간별 통계 이미 집계됨, 스킵 - locationId:{}, logHour:{}", locationId, windowStart);
                    continue;
                }

                hourlyTelemetryStatService.create(new HourlyTelemetryStatCreateRequest(
                        locationId,
                        windowStart,
                        toJson(avgByLocation.getOrDefault(locationId, Map.of())),
                        toJson(maxByLocation.getOrDefault(locationId, Map.of())),
                        toJson(minByLocation.getOrDefault(locationId, Map.of())),
                        actuatorByLocation.containsKey(locationId) ? toJson(actuatorByLocation.get(locationId)) : null
                ));
            } catch (Exception e) {
                log.error("시간별 통계 저장 실패 - locationId:{}, logHour: {}", locationId, windowStart, e);
            }
        }
    }

    /**
     * sensor_data를 location_id/필드별로 그룹핑해 평균/최고/최저 중 하나를 집계한다.
     *
     * @param aggFn Flux 집계 함수명("mean"/"max"/"min")
     * @param start 집계 시작 시각(포함)
     * @param end   집계 종료 시각(미포함)
     * @return locationId → (필드명 → 집계값) 맵
     */
    private Map<Long, Map<String, Double>> querySensorAggregate(String aggFn, OffsetDateTime start,
                                                                OffsetDateTime end) {
        String flux = """
                from(bucket: "%s")
                    |> range(start: %s, stop: %s)
                    |> filter(fn: (r) => r._measurement == "sensor_data")
                    |> group(columns: ["location_id", "_field"])
                    |> %s()
                """.formatted(bucket, toRfc3339(start), toRfc3339(end), aggFn);

        Map<Long, Map<String, Double>> result = new HashMap<>();

        for (FluxTable table : influxDBClient.getQueryApi().query(flux)) {
            for (FluxRecord fluxRecord : table.getRecords()) {
                Long locationId = Long.valueOf(
                        (String) Objects.requireNonNull(fluxRecord.getValueByKey("location_id")));

                String field = fluxRecord.getField();
                Double value = ((Number) Objects.requireNonNull(fluxRecord.getValue())).doubleValue();

                result.computeIfAbsent(locationId, key -> new HashMap<>()).put(field, value);
            }
        }
        return result;
    }

    /**
     * actuator_status(location_id/actuator_type별 ON-OFF 상태 시계열)를 integral 연산해 시간당 누적 가동 분(분 단위)을 산출한다.
     * <p>Core/시뮬레이터 엔진이 POWER_STATUS 전환 시점 + 매 정각 하트비트로 이 measurement에 상태를 기록해준다는
     * 전제하에 동작한다. 아직 실제 데이터가 없으면 빈 결과가 반환되고, 그 location의 actuatorOnMinutes는 {@code null}로 저장된다.
     *
     * @param start 집계 시작 시각(포함)
     * @param end   집계 종료 시각(미포함)
     * @return locationId → (actuator_type → 가동 분) 맵
     */
    private Map<Long, Map<String, Double>> queryActuatorMinutes(OffsetDateTime start, OffsetDateTime end) {
        String flux = """
                from(bucket: "%s")
                |> range(start: %s, stop: %s)
                |> filter(fn: (r) => r._measurement == "actuator_status")
                |> filter(fn: (r) => r._field == "status_value")
                |> group(columns: ["location_id", "actuator_type"])
                |> integral(unit: 1m)
                """.formatted(bucket, toRfc3339(start), toRfc3339(end));

        Map<Long, Map<String, Double>> result = new HashMap<>();

        for (FluxTable table : influxDBClient.getQueryApi().query(flux)) {
            for (FluxRecord fluxRecord : table.getRecords()) {
                Long locationId = Long.valueOf(
                        (String) Objects.requireNonNull(fluxRecord.getValueByKey("location_id")));
                String actuatorType = (String) fluxRecord.getValueByKey("actuator_type");
                Double onMinutes = ((Number) Objects.requireNonNull(fluxRecord.getValue())).doubleValue();

                result.computeIfAbsent(locationId, key -> new HashMap<>()).put(actuatorType, onMinutes);
            }
        }
        return result;
    }

    /**
     * Flux {@code range()}가 요구하는 RFC3339 형식 문자열로 변환.
     */
    private String toRfc3339(OffsetDateTime time) {
        return time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 집계 결과 맵을 hourly_telemetry_stats의 JSONB 컬럼(metrics_avg/max/min, actuator_on_minutes)에 저장할 JSON 문자열로 직렬화.
     */
    private String toJson(Map<String, Double> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            throw new IllegalStateException("메트릭 JSON 직렬화 실패", e);
        }
    }
}
