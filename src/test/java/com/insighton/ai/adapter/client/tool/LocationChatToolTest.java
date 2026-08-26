package com.insighton.ai.adapter.client.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.AutoControlMode;
import com.insighton.ai.adapter.client.dto.LocationResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class LocationChatToolTest {

    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private LocationChatTool locationChatTool;

    @Test
    void getLocations_toolContext의_groupId로_조회해_한줄_문자열로_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        LocationResponse response = new LocationResponse(42L, "사무실1", 5L, AutoControlMode.SUGGESTION);
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of(response));

        String result = locationChatTool.getLocations(toolContext);

        assertThat(result).isEqualTo("id=42 | 사무실1 | SUGGESTION");
    }

    @Test
    void getLocations_그룹에_위치가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(coreClient.getLocationsByGroup(5L)).willReturn(List.of());

        String result = locationChatTool.getLocations(toolContext);

        assertThat(result).isEqualTo("조회된 위치 없음");
    }
}
