package com.insighton.ai.domain.enginealert.event;

import com.insighton.ai.domain.enginealert.entity.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.hibernate.validator.constraints.Length;

@Schema(description = "Rule Engine ALERT_ACTION 노드가 발행하는 알람 생성 이벤트 (RabbitMQ 수신 겸 내부 생성 요청)")
public record EngineAlertActionEvent(

        @Schema(description = "이벤트 고유 ID(UUID). 발화 시점에 한 번만 생성하고, 발행 재시도 시에도 동일한 값을 재사용해야 함")
        @NotBlank
        String eventId,

        @Schema(description = "그룹 ID", example = "5")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        @NotNull
        Long locationId,

        @Schema(description = "발화한 flow ID", example = "3")
        @NotNull
        Long flowId,

        @Schema(description = "제목 (flow 작성자가 노드 설정에서 입력)")
        @NotBlank
        @Length(max = 200)
        String title,

        @Schema(description = "상세 메시지 (flow 작성자가 노드 설정에서 입력)")
        @NotBlank
        String message,

        @Schema(description = "심각도")
        @NotNull
        Severity severity,

        @Schema(description = "알람을 유발한 실제 센서값 (예: {\"temperature\":29.5,\"magnet_status\":\"OPEN\"}). "
                + "SCHEDULE_TRIGGER처럼 센서값이 없으면 null 허용")
        Map<String, Object> triggerValue
) {
}