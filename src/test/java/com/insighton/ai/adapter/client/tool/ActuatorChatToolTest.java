package com.insighton.ai.adapter.client.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.LocationResolver;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.ActuatorType;
import com.insighton.ai.adapter.client.dto.CallerService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class ActuatorChatToolTest {

    @Mock
    private CoreClient coreClient;

    @Mock
    private LocationResolver locationResolver;

    private ActuatorChatTool actuatorChatTool;

    @BeforeEach
    void setUp() {
        actuatorChatTool = new ActuatorChatTool(coreClient, locationResolver);
    }

    @Test
    void controlActuator_locationName_없이_대화의_현재_위치로_조작한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));

        String result = actuatorChatTool.controlActuator(null, ActuatorType.AIRCON, "POWER_STATUS", "ON",
                toolContext);

        assertThat(result).isEqualTo("조작 완료: AIRCON POWER_STATUS=ON");
    }

    @Test
    void controlActuator_locationName으로_위치를_찾아_조작한다() {
        given(locationResolver.resolveIdByName(5L, "3층 회의실")).willReturn(Optional.of(42L));
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        String result = actuatorChatTool.controlActuator("3층 회의실", ActuatorType.AIRCON, "POWER_STATUS", "ON",
                toolContext);

        assertThat(result).isEqualTo("조작 완료: AIRCON POWER_STATUS=ON");
    }

    @Test
    void controlActuator_locationName으로_위치를_못찾으면_안내_문구를_반환한다() {
        given(locationResolver.resolveIdByName(5L, "옥상")).willReturn(Optional.empty());
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        String result = actuatorChatTool.controlActuator("옥상", ActuatorType.AIRCON, "POWER_STATUS", "ON",
                toolContext);

        assertThat(result).isEqualTo("위치를 찾을 수 없습니다: 옥상");
    }

    @Test
    void controlActuator_대화에_위치가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        String result = actuatorChatTool.controlActuator(null, ActuatorType.AIRCON, "POWER_STATUS", "ON",
                toolContext);

        assertThat(result).contains("어느 위치를 말하는지 알 수 없어");
    }

    @Test
    void controlActuator_허용되지_않은_명령이면_안내_문구를_반환한다() {
        // SET_TEMPERATURE=999는 ActuatorCommandRequest.of() 내부 실제 검증(ActuatorCommandVocabulary.validate)에서
        // 범위(18~30) 밖 값으로 걸러진다 - coreClient까지 도달하지 않으므로 별도 스텁이 필요 없다.
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));

        String result = actuatorChatTool.controlActuator(null, ActuatorType.AIRCON, "SET_TEMPERATURE", "999",
                toolContext);

        assertThat(result).contains("허용되지 않은 명령 조합입니다");
    }

    @Test
    void controlActuator_성공하면_CoreClient에_AI_SYSTEM_주체로_명령을_전달한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));

        actuatorChatTool.controlActuator(null, ActuatorType.AIRCON, "POWER_STATUS", "ON", toolContext);

        org.mockito.Mockito.verify(coreClient).executeActuatorCommand(5L, 42L,
                ActuatorCommandRequest.of("AIRCON", "POWER_STATUS", "ON", CallerService.AI_SYSTEM));
    }
}
