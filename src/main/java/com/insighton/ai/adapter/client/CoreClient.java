package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.ActionPayload;
import com.insighton.ai.adapter.client.dto.ActuatorRunLogResponse;
import com.insighton.ai.adapter.client.dto.GroupMemberResponse;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.adapter.client.dto.WeatherResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-core", path = "/internal/v1", url = "${core-service.url}")
public interface CoreClient {

    @GetMapping("/groups/{group-id}/members")
    GroupMemberResponse getGroupMemberByUserId(@PathVariable("group-id") Long groupId,
                                               @RequestParam("userId") Long userId);

    @GetMapping("/groups/{group-id}/locations")
    List<LocationResponse> getLocationsByGroup(@PathVariable("group-id") Long groupId);

    @GetMapping("/locations/{location-id}")
    LocationResponse getLocation(@PathVariable("location-id") Long locationId);


    /**
     * 리포트 생성 배치가 기간 내 설정 온도 변경 이력/조작 주체 비율을 계산하기 위해 사용하는 원본 조작 로그 조회. Core actuator_run_logs를 location_id 기준으로 필터링해 그대로
     * 내려준다(집계는 AI 쪽에서 처리).
     */
    @GetMapping("/actuators/run-logs")
    List<ActuatorRunLogResponse> getActuatorRunLogs(@RequestParam("locationIds") List<Long> locationIds,
                                                    @RequestParam("from")
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    OffsetDateTime from,
                                                    @RequestParam("to")
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    OffsetDateTime to);


    /**
     * AI가 액츄에이터 조작 시 호출할 API ex) 에어컨 ON / OFF 등
     *
     * @param actionPayload 액츄에이터 조작용 DTO
     */
    @PostMapping("/actuators/commands")
    void executeActuatorCommand(@RequestBody ActionPayload actionPayload);


    /**
     * 제안 생성 AI 판단용 실외 날씨 / 미세먼지 조회
     */
    @GetMapping("/groups/{group-id}/weather")
    WeatherResponse getWeather(@PathVariable("group-id") Long groupId);
}
