package com.insighton.ai.domain.enginealert.dto;

import java.util.List;

public record EngineAlertSummary(
        long criticalCount,
        long warningCount,
        List<String> topAlertTitles
) {
}
