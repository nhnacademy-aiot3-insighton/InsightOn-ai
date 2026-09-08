package com.insighton.ai.domain.scheduledtask.tool;

import com.insighton.ai.adapter.client.LocationResolver;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ScheduledComfortSetupChatTool {

    private static final String NO_LOCATION_MESSAGE = "이 대화에서 어느 위치를 말하는지 알 수 없어 예약할 수 없습니다. "
            + "사용자에게 어느 위치인지 물어보세요.";
    // ponytail: 고정 리드타임/최대 예약범위. 액추에이터별 예열시간 차등화는 실측 데이터 쌓이면 그때.
    private static final Duration LEAD_TIME = Duration.ofMinutes(30);
    private static final Duration MAX_LEAD = Duration.ofDays(7);

    private final LocationResolver locationResolver;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Tool(description = "사용자가 특정 시각까지 공간을 쾌적하게 준비해달라고 요청하면 1회성으로 예약한다(반복 자동화 아님). "
            + "\"오늘 오후 3시에 회의 있어\" 같은 표현에서 오늘 날짜 기준으로 구체적인 ISO 시각을 계산해 targetDateTime에 넘겨라. "
            + "7일 이내 시각만 예약 가능하다.")
    public String scheduleComfortSetup(
            @ToolParam(description = "대상 위치 이름. 안 주면 대화의 현재 위치 사용", required = false) String locationName,
            @ToolParam(description = "목표 시각(ISO-8601, 예: 2026-09-02T15:00:00+09:00)") OffsetDateTime targetDateTime,
            @ToolParam(description = "사용자가 말한 목적(회의, 행사 등)", required = false) String purposeText,
            ToolContext toolContext
    ) {
        Long groupId = (Long) toolContext.getContext().get("groupId");
        Long contextLocationId = (Long) toolContext.getContext().get("locationId");

        Long locationId;
        if (locationName != null) {
            Optional<Long> resolved = locationResolver.resolveIdByName(groupId, locationName);
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

        OffsetDateTime now = OffsetDateTime.now();
        if (targetDateTime.isBefore(now)) {
            return "이미 지난 시각입니다: " + targetDateTime;
        }
        if (targetDateTime.isAfter(now.plus(MAX_LEAD))) {
            return "7일 이내 시각만 예약할 수 있습니다.";
        }

        OffsetDateTime triggerAt = targetDateTime.minus(LEAD_TIME);
        if (triggerAt.isBefore(now)) {
            triggerAt = now;
        }

        ScheduledActuatorTask task = new ScheduledActuatorTask(
                UUID.randomUUID().toString(), groupId, locationId, purposeText, 0);
        redisTemplate.opsForZSet().add(ScheduledActuatorTask.REDIS_KEY,
                jsonMapper.writeValueAsString(task), triggerAt.toEpochSecond());

        return "%s에 맞춰 %s부터 준비하겠습니다.".formatted(targetDateTime, triggerAt);
    }
}
