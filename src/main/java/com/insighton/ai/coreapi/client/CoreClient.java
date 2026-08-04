package com.insighton.ai.coreapi.client;

import com.insighton.ai.coreapi.dto.ActuatorRunLogResponse;
import com.insighton.ai.coreapi.dto.GroupMemberResponse;
import com.insighton.ai.coreapi.dto.LocationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-core", path = "/internal")
public interface CoreClient {

    @GetMapping("/groups/{group-id}/members/user/{user-id}")
    GroupMemberResponse getGroupMemberByUserId(@PathVariable("group-id") Long groupId,
                                               @PathVariable("user-id") Long userId);

    @GetMapping("/locations/{location-id}")
    LocationResponse getLocation(@PathVariable("location-id") Long locationId);

    /**
     * 리포트 생성 배치가 기간 내 설정 온도 변경 이력/조작 주체 비율을 계산하기 위해 사용하는 원본 조작 로그 조회.
     * Core actuator_run_logs를 location_id 기준으로 필터링해 그대로 내려준다(집계는 AI 쪽에서 처리).
     */
    @GetMapping("/actuators/run-logs")
    List<ActuatorRunLogResponse> getActuatorRunLogs(@RequestParam("locationIds") List<Long> locationIds,
                                                     @RequestParam("from") OffsetDateTime from,
                                                     @RequestParam("to") OffsetDateTime to);
}
