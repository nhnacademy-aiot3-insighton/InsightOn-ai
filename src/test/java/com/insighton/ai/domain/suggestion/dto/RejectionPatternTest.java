package com.insighton.ai.domain.suggestion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RejectionPatternTest {

    @Test
    void 필드값을_그대로_보관한다() {
        RejectionPattern pattern = new RejectionPattern("AIRCON", "POWER_STATUS", "OFF", 3L, 5L);

        assertThat(pattern.actuatorType()).isEqualTo("AIRCON");
        assertThat(pattern.command()).isEqualTo("POWER_STATUS");
        assertThat(pattern.commandValue()).isEqualTo("OFF");
        assertThat(pattern.rejectedCount()).isEqualTo(3L);
        assertThat(pattern.totalCount()).isEqualTo(5L);
    }
}
