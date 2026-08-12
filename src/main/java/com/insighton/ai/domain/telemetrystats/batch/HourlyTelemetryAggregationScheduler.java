package com.insighton.ai.domain.telemetrystats.batch;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.exceptions.InfluxException;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatCreateRequest;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매시 정각, 직전 1시간의 InfluxDB {@code sensor_data}/{@code actuator_status} 원시 데이터를 location_id 기준으로 집계해
 * hourly_telemetry_stats에 저장.
 * <p>Core locations 조회 없이, InfluxDB에 실제로 데이터가 들어온 location_id 태그만으로 집계 대상을 확보
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HourlyTelemetryAggregationScheduler {

    private static final int LOOKBACK_HOURS = 3;
    private static final String NON_NUMERIC_FIELD_TYPE_MISMATCH_HINT = "unsupported aggregate column type";
    private static final String NON_NUMERIC_FIELD_UNSUPPORTED_AGG_HINT = "unsupported for aggregate";

    private final InfluxDBClient influxDBClient;
    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final JsonMapper jsonMapper = new JsonMapper();

    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * 정각 배치 진입점. 직전 {@value LOOKBACK_HOURS}개 시간 창을 매번 다시 훑어, sensor_data 기준 평균/최고/최저와 actuator_status 기준 가동 분을 조회한 뒤
     * location별로 저장. 이미 저장된 (location, logHour)는 findByLocationAndLogHour로 건너뜀, 이전 실행에서 예외로 실패했던 시간 창도 자동으로 재시도된다.
     * location 하나가 실패해도 나머지는 계속 처리한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "hourlyTelemetryAggregation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void aggregate() {
        OffsetDateTime latestWindowEnd = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS);

        for (int i = 0; i < LOOKBACK_HOURS; i++) {
            OffsetDateTime windowEnd = latestWindowEnd.minusHours(i);
            OffsetDateTime windowStart = windowEnd.minusHours(1);
            try {
                aggregateWindow(windowStart, windowEnd);
            } catch (Exception e) {
                log.error("시간 창 집계 실패, 다음 창으로 계속 진행 - windowStart:{}", windowStart, e);
            }
        }
    }

    /**
     * 지정된 1시간 구간을 집계해 location별로 저장한다. 이미 집계된 (location, logHour)는 스킵하고, location 하나가 실패해도 나머지 location은 계속 처리한다.
     */
    private void aggregateWindow(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
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
                log.error("시간별 통계 저장 실패 - locationId:{}, logHour:{}", locationId, windowStart, e);
            }
        }
    }

    /**
     * sensor_data를 location_id/필드별로 그룹핑해 평균/최고/최저 중 하나를 집계한다. 필드마다 개별 쿼리를 날려서, 자석 센서의 OPEN/CLOSED 같은 비숫자 필드가 섞여 있어도 그
     * 필드만 건너뛰고 나머지 숫자 필드는 계속 집계되게 한다 — 어떤 필드가 숫자인지 미리 알 필요 없이, 새 필드가 추가돼도 그대로 동작한다.
     *
     * @param aggFn Flux 집계 함수명("mean"/"max"/"min")
     * @param start 집계 시작 시각(포함)
     * @param end   집계 종료 시각(미포함)
     * @return locationId → (필드명 → 집계값) 맵
     */
    private Map<Long, Map<String, Double>> querySensorAggregate(String aggFn, OffsetDateTime start,
                                                                OffsetDateTime end) {
        Map<Long, Map<String, Double>> result = new HashMap<>();

        for (String field : findSensorFields(start, end)) {
            try {
                mergeFieldAggregate(result, field, aggFn, start, end);
            } catch (NonNumericFieldException e) {
                log.warn("필드 집계 스킵(숫자형이 아님) - field:{}, aggFn:{}", field, aggFn);
            }
        }
        return result;
    }

    /**
     * 이번 시간 창에 sensor_data measurement에 실제로 존재하는 필드명 목록을 조회한다. 값 타입과 무관하게 전부 반환하고, 숫자 여부 판별은
     * {@link #mergeFieldAggregate} 호출 시점의 성공/실패로 결정한다.
     */
    private Set<String> findSensorFields(OffsetDateTime start, OffsetDateTime end) {
        String flux = """
                from(bucket: "%s")
                    |> range(start: %s, stop: %s)
                    |> filter(fn: (r) => r._measurement == "sensor_data")
                    |> group(columns: ["_field"])
                    |> limit(n: 1)
                    |> keep(columns: ["_field"])
                """.formatted(bucket, toRfc3339(start), toRfc3339(end));

        Set<String> fields = new HashSet<>();
        for (FluxTable table : influxDBClient.getQueryApi().query(flux)) {
            for (FluxRecord fluxRecord : table.getRecords()) {
                fields.add(fluxRecord.getField());
            }
        }
        return fields;
    }

    /**
     * 필드 하나에 대해서만 집계 쿼리를 실행해 result에 병합한다. 이 필드가 문자열 등 비숫자 타입으로 확인된 경우에만
     * {@link NonNumericFieldException}을 던져 호출부(querySensorAggregate)가 그 필드만 건너뛰게 한다. 연결/서버 오류,
     * Flux 문법 오류 등 진짜 오류는 그대로 전파해 상위(aggregate)에서 이번 시간 창 전체를 재시도하게 한다.
     */
    private void mergeFieldAggregate(Map<Long, Map<String, Double>> result, String field, String aggFn,
                                     OffsetDateTime start, OffsetDateTime end) {
        String flux = """
                from(bucket: "%s")
                    |> range(start: %s, stop: %s)
                    |> filter(fn: (r) => r._measurement == "sensor_data" and r._field == "%s")
                    |> group(columns: ["location_id"])
                    |> %s()
                """.formatted(bucket, toRfc3339(start), toRfc3339(end), escapeFluxString(field), aggFn);

        List<FluxTable> tables;
        try {
            tables = influxDBClient.getQueryApi().query(flux);
        } catch (InfluxException e) {
            if (isNonNumericFieldError(e)) {
                throw new NonNumericFieldException(field, e);
            }
            throw e;
        }

        for (FluxTable table : tables) {
            for (FluxRecord fluxRecord : table.getRecords()) {
                Long locationId = Long.valueOf(
                        (String) Objects.requireNonNull(fluxRecord.getValueByKey("location_id")));
                Object rawValue = Objects.requireNonNull(fluxRecord.getValue());
                if (!(rawValue instanceof Number number)) {
                    // max()/min()은 문자열 필드에서도 사전순 비교로 쿼리 자체는 성공할 수 있어 결과 타입으로 걸러냄
                    throw new NonNumericFieldException(field);
                }

                result.computeIfAbsent(locationId, key -> new HashMap<>()).put(field, number.doubleValue());
            }
        }
    }

    /**
     * InfluxDB가 비숫자 필드 집계 시 던지는 걸로 확인된 오류 패턴을 판별한다. mean()은 BadRequestException(400,
     * "unsupported aggregate column type string")을, max()/min()은 InternalServerErrorException(500,
     * "panic: unsupported for aggregate max: ...")을 던지는 등 집계 함수마다 실패 형태가 달라 메시지 기반으로 함께 판별한다.
     */
    private boolean isNonNumericFieldError(InfluxException e) {
        String message = e.getMessage();
        return message != null
                && (message.contains(NON_NUMERIC_FIELD_TYPE_MISMATCH_HINT)
                    || message.contains(NON_NUMERIC_FIELD_UNSUPPORTED_AGG_HINT));
    }

    /**
     * Flux 문자열 리터럴에 필드명을 안전하게 삽입하기 위한 이스케이프. 필드명에 {@code "}나 {@code \}가 포함돼 있으면
     * 이스케이프 없이는 Flux 쿼리 문자열이 깨진다.
     */
    private String escapeFluxString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * {@link #mergeFieldAggregate}가 특정 필드를 숫자형이 아니라고 확정했을 때만 던지는 마커 예외. 연결 오류 등
     * 다른 원인의 실패와 구분하기 위한 용도로, {@link #querySensorAggregate}에서 이 타입만 잡아 필드를 건너뛴다.
     */
    private static class NonNumericFieldException extends RuntimeException {
        NonNumericFieldException(String field) {
            super("비숫자 필드: " + field);
        }

        NonNumericFieldException(String field, Throwable cause) {
            super("비숫자 필드: " + field, cause);
        }
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
            return jsonMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            throw new IllegalStateException("메트릭 JSON 직렬화 실패", e);
        }
    }
}
