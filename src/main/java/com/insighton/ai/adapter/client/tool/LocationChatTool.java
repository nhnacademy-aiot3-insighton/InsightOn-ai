package com.insighton.ai.adapter.client.tool;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocationChatTool {

    private final CoreClient coreClient;

    @Tool(description = "현재 그룹에 속한 모든 위치의 목록(이름, 자동제어 모드)을 조회한다.")
    public List<LocationResponse> getLocations(ToolContext toolContext) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        return coreClient.getLocationsByGroup(groupId);
    }
}
