package com.insighton.ai.adapter.client.tool;

import com.insighton.ai.adapter.client.CoreClient;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocationChatTool {

    private final CoreClient coreClient;

    @Tool(description = "현재 그룹에 속한 모든 위치의 목록을 조회한다. "
            + "결과는 'id=위치ID | 이름 | 자동제어모드' 형식의 한 줄씩이다.")
    public String getLocations(ToolContext toolContext) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        var locations = coreClient.getLocationsByGroup(groupId);
        if (locations.isEmpty()) {
            return "조회된 위치 없음";
        }
        return locations.stream()
                .map(l -> "id=%d | %s | %s".formatted(l.locationId(), l.locationName(), l.autoControlMode()))
                .collect(Collectors.joining("\n"));
    }
}
