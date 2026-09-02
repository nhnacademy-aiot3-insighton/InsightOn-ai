package com.insighton.ai.domain.scheduledtask.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.insighton.ai.adapter.client.LocationResolver;
import com.insighton.ai.domain.scheduledtask.dto.ScheduledActuatorTask;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ScheduledComfortSetupChatToolTest {

    @Mock
    private LocationResolver locationResolver;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ScheduledComfortSetupChatTool scheduledComfortSetupChatTool;

    @BeforeEach
    void setUp() {
        scheduledComfortSetupChatTool = new ScheduledComfortSetupChatTool(
                locationResolver, redisTemplate, new JsonMapper());
    }

    @Test
    void scheduleComfortSetup_locationName도_없고_context에_locationId도_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));

        String result = scheduledComfortSetupChatTool.scheduleComfortSetup(
                null, OffsetDateTime.now().plusHours(1), null, toolContext);

        assertThat(result).isEqualTo("이 대화에서 어느 위치를 말하는지 알 수 없어 예약할 수 없습니다. "
                + "사용자에게 어느 위치인지 물어보세요.");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void scheduleComfortSetup_locationName과_일치하는_위치가_없으면_안내_문구를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(locationResolver.resolveIdByName(5L, "없는위치")).willReturn(Optional.empty());

        String result = scheduledComfortSetupChatTool.scheduleComfortSetup(
                "없는위치", OffsetDateTime.now().plusHours(1), null, toolContext);

        assertThat(result).isEqualTo("위치를 찾을 수 없습니다: 없는위치");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void scheduleComfortSetup_locationName이_있으면_context_locationId보다_우선한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        given(locationResolver.resolveIdByName(5L, "3층 회의실")).willReturn(Optional.of(99L));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        OffsetDateTime target = OffsetDateTime.now().plusHours(2);

        scheduledComfortSetupChatTool.scheduleComfortSetup("3층 회의실", target, null, toolContext);

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOperations).add(eq(ScheduledActuatorTask.REDIS_KEY), memberCaptor.capture(), anyDouble());
        ScheduledActuatorTask savedTask = new JsonMapper().readValue(memberCaptor.getValue(), ScheduledActuatorTask.class);
        assertThat(savedTask.locationId()).isEqualTo(99L);
    }

    @Test
    void scheduleComfortSetup_목표시각이_과거면_예약하지_않고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        OffsetDateTime past = OffsetDateTime.now().minusHours(1);

        String result = scheduledComfortSetupChatTool.scheduleComfortSetup(null, past, null, toolContext);

        assertThat(result).isEqualTo("이미 지난 시각입니다: " + past);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void scheduleComfortSetup_7일_이후면_예약하지_않고_안내한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        OffsetDateTime tooFar = OffsetDateTime.now().plusDays(8);

        String result = scheduledComfortSetupChatTool.scheduleComfortSetup(null, tooFar, null, toolContext);

        assertThat(result).isEqualTo("7일 이내 시각만 예약할 수 있습니다.");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void scheduleComfortSetup_정상_요청이면_리드타임만큼_당겨서_예약한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        OffsetDateTime target = OffsetDateTime.now().plusHours(2);

        String result = scheduledComfortSetupChatTool.scheduleComfortSetup(null, target, "회의", toolContext);

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).add(eq(ScheduledActuatorTask.REDIS_KEY), memberCaptor.capture(), scoreCaptor.capture());

        ScheduledActuatorTask savedTask = new JsonMapper().readValue(memberCaptor.getValue(), ScheduledActuatorTask.class);
        assertThat(savedTask.groupId()).isEqualTo(5L);
        assertThat(savedTask.locationId()).isEqualTo(42L);
        assertThat(savedTask.purposeText()).isEqualTo("회의");
        assertThat(savedTask.attemptCount()).isZero();

        OffsetDateTime expectedTriggerAt = target.minusMinutes(30);
        assertThat(scoreCaptor.getValue()).isEqualTo((double) expectedTriggerAt.toEpochSecond());
        assertThat(result).contains(target.toString()).contains(expectedTriggerAt.toString());
    }

    @Test
    void scheduleComfortSetup_리드타임을_당기면_과거가_되면_지금으로_보정한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L, "locationId", 42L));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        OffsetDateTime target = OffsetDateTime.now().plusMinutes(10);

        scheduledComfortSetupChatTool.scheduleComfortSetup(null, target, null, toolContext);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).add(eq(ScheduledActuatorTask.REDIS_KEY), anyString(), scoreCaptor.capture());

        OffsetDateTime triggerAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(scoreCaptor.getValue().longValue()), ZoneId.systemDefault());
        assertThat(triggerAt).isCloseTo(OffsetDateTime.now(), byLessThan(5, ChronoUnit.SECONDS));
    }
}
