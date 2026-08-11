package com.insighton.ai.domain.telemetrystats.tool;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import com.insighton.ai.domain.telemetrystats.service.HourlyTelemetryStatService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelemetryStatChatTool {

    private static final String NO_LOCATION_MESSAGE = "이 대화에서 어느 위치를 말하는지 알 수 없어 센서 통계를 조회할 수 없습니다. "
            + "사용자에게 어느 위치의 통계를 원하는지 물어보세요.";

    private final HourlyTelemetryStatService hourlyTelemetryStatService;
    private final CoreClient coreClient;

    @Tool(description = "지정된 기간의 센서 지표(온도/습도/CO2 등) 평균·최고·최저값과 액추에이터별 가동 시간을 조회한다. "
            + "locationName으로 위치를 이름으로 지정할 수 있고, 안 주면 대화에 지정된 현재 위치를 쓴다. "
            + "from/to를 지정 안 하면 최근 7일로 조회한다.")
    public Object getStats(
            @ToolParam(description = "조회할 위치 이름. getLocations로 받은 목록 중 하나. 지정 안 하면 대화의 현재 위치를 사용",
                    required = false) String locationName,
            @ToolParam(description = "조회 시작 시각(ISO-8601). 지정 안 하면 7일 전부터", required = false) OffsetDateTime from,
            @ToolParam(description = "조회 종료 시각(ISO-8601). 지정 안 하면 지금까지", required = false) OffsetDateTime to,
            ToolContext toolContext) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long contextLocationId = (Long) toolContext.getContext().get("locationId");

        Long locationId;
        if (locationName != null) {
            Optional<Long> resolved = resolveLocationIdByName(groupId, locationName);
            if (resolved.isEmpty()) {
                return "위치를 찾을 수 없습니다: " + locationName;
            }
            locationId = resolved.get();
        } else {
            locationId = contextLocationId;
        }

        if (locationId == null) {
            return NO_LOCATION_MESSAGE;
        }

        OffsetDateTime resolvedFrom = from != null ? from : OffsetDateTime.now().minusDays(7);
        OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now();
        return hourlyTelemetryStatService.summarizePeriod(locationId, resolvedFrom, resolvedTo);
    }

    private Optional<Long> resolveLocationIdByName(Long groupId, String locationName) {
        List<LocationResponse> locations = coreClient.getLocationsByGroup(groupId);

        Optional<LocationResponse> exactMatch = locations.stream()
                .filter(location -> location.locationName().equalsIgnoreCase(locationName))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.map(LocationResponse::locationId);
        }

        String normalizedQuery = locationName.toLowerCase(Locale.ROOT);
        return locations.stream()
                .filter(location -> location.locationName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(LocationResponse::locationId)
                .findFirst();
    }
}
