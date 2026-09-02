package com.insighton.ai.adapter.client.tool;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.LocationResponse;
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
public class ActuatorChatTool {

    private static final String NO_LOCATION_MESSAGE = "이 대화에서 어느 위치를 말하는지 알 수 없어 액추에이터를 조작할 수 없습니다. "
            + "사용자에게 어느 위치인지 물어보세요.";

    private final CoreClient coreClient;

    @Tool(description = "지정된 위치의 액추에이터를 조작한다. locationName으로 위치를 이름으로 지정할 수 있고,"
            + "안 주면 대화에 지정된 현재 위치를 쓴다. actuatorType/command/ commandValue는 반드시 아래 조합만 쓴다 \n"
            + "AIRCON: POWER_STATUS(ON/OFF), OPERATION_MODE(COOL/DRY/FAN/AUTO), SET_TEMPERATURE(18~30)\n"
            + "AIR_PURIFIER: POWER_STATUS(ON/OFF), OPERATION_MODE(AUTO/SLEEP/TURBO)\n"
            + "VENTILATION_FAN: POWER_STATUS(ON/OFF), OPERATION_MODE(LOW/MID/HIGH)")
    public String controlActuator(
            @ToolParam(description = "조작할 위치 이름. 지정 안 하면 대화의 현재 위치를 사용", required = false) String locationName,
            @ToolParam(description = "액추에이터 종류: AIRCON, AIR_PURIFIER, VENTILATION_FAN") ActuatorType actuatorType,
            @ToolParam(description = "명령 종류: POWER_STATUS, OPERATION_MODE, SET_TEMPERATURE") String command,
            @ToolParam(description = "명령 값. 위 허용값 목록 중 하나(예: ON, COOL, 23)") String commandValue,
            ToolContext toolContext
    ) {

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

        coreClient.executeActuatorCommand(groupId, locationId,
                ActuatorCommandRequest.of(actuatorType.name(), command, commandValue, CallerService.AI_SYSTEM));

        return "조작 완료: " + actuatorType + " " + command + "=" + commandValue;
    }

    private Optional<Long> resolveLocationIdByName(Long groupId, String locationName) {
        List<LocationResponse> locations = coreClient.getLocationsByGroup(groupId);

        Optional<LocationResponse> exactMatch = locations.stream()
                .filter(location ->
                        location.locationName().equalsIgnoreCase(locationName))
                .findFirst();

        if (exactMatch.isPresent()) {
            return exactMatch.map(LocationResponse::locationId);
        }

        String normalizedQuery = locationName.toLowerCase(Locale.ROOT);
        return locations.stream()
                .filter(location ->
                        location.locationName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(LocationResponse::locationId)
                .findFirst();
    }
}
