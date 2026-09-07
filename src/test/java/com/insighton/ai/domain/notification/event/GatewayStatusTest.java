package com.insighton.ai.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayStatusTest {

    @Test
    void ACTIVE와_FAULT_두_상태만_존재한다() {
        assertThat(GatewayStatus.values()).containsExactly(GatewayStatus.ACTIVE, GatewayStatus.FAULT);
    }

    @Test
    void 이름으로_역직렬화된다() {
        assertThat(GatewayStatus.valueOf("FAULT")).isEqualTo(GatewayStatus.FAULT);
    }
}
