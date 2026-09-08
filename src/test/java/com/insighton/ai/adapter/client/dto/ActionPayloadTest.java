package com.insighton.ai.adapter.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ActionPayloadTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void 신규_포맷은_actions_그대로_역직렬화된다() {
        String json = """
                {"locationId":1,"actions":[{"actuatorType":"AIRCON","command":"POWER_STATUS","commandValue":"OFF"}]}
                """;

        ActionPayload payload = jsonMapper.readValue(json, ActionPayload.class);

        assertThat(payload.locationId()).isEqualTo(1L);
        assertThat(payload.actions()).containsExactly(
                new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "OFF"));
    }

    @Test
    void 옛_단일_액추에이터_포맷은_actions_1건으로_복원된다() {
        String json = """
                {"locationId":1,"actuatorType":"AIRCON","command":"POWER_STATUS","commandValue":"OFF"}
                """;

        ActionPayload payload = jsonMapper.readValue(json, ActionPayload.class);

        assertThat(payload.locationId()).isEqualTo(1L);
        assertThat(payload.actions()).containsExactly(
                new ActuatorAction(ActuatorType.AIRCON, "POWER_STATUS", "OFF"));
    }

    @Test
    void actions와_옛_필드_둘_다_없으면_빈_리스트() {
        String json = "{\"locationId\":1}";

        ActionPayload payload = jsonMapper.readValue(json, ActionPayload.class);

        assertThat(payload.actions()).isEqualTo(List.of());
    }
}
