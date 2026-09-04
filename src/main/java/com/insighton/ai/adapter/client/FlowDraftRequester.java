package com.insighton.ai.adapter.client;

import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_END;
import static com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary.BUSINESS_HOUR_START;

import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.ActuatorCommandVocabulary;
import com.insighton.ai.adapter.client.dto.FlowDraftCreateRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftLinkRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftNodeRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftResponse;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import feign.FeignException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 월간 리포트가 찾은 시간대 패턴을 근거로 Rule Engine에 예방적 자동화 flow 초안(SCHEDULE → ACTUATOR_CONTROL 고정 템플릿)을 요청한다. Rule Engine
 * 미응답/장애가 리포트 생성 자체를 막으면 안 되므로, 실패는 로그만 남기고 예외를 던지지 않는다(호출부에서 try-catch할 필요 없음).
 *
 * <p>Rule Engine의 flows 테이블에 생성 주체·출처를 담을 컬럼이 없어서(스키마 변경 불가 - rule-engine-flow-draft-request 참고),
 * "AI가 만들었다"는 사실과 어느 리포트 기반인지는 description 문장 안에 사람이 읽는 텍스트로만 남긴다 - 프로그램적으로 조회는 못 하지만
 * 사용자가 대시보드에서 이 flow를 볼 때 출처를 이해하는 데는 지장 없다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlowDraftRequester {

    private static final int PREVENTIVE_LEAD_HOURS = 1;
    // ponytail: 정적 3시간 버킷이라 경계에 걸치는 케이스(예: 11시/12시)는 여전히 다른 슬롯으로 갈릴 수 있음.
    // 완전한 해법은 AI가 기존 flow의 실제 트리거 시각과 비교하는 것이지만 Rule Engine에 조회 API가 없어서
    // 보류(rule-engine-flow-duplicate-check-request §3.3 참고). 진동(oscillation) 문제 해소가 우선.
    private static final int TIME_SLOT_HOURS = 3;

    private final RuleEngineClient ruleEngineClient;

    /**
     * @return 생성/갱신된 flow의 실제 상태(ACTIVE/INACTIVE, Rule Engine의 AutoControlMode 판단 결과). 요청 실패 시 빈 값.
     */
    public Optional<String> requestDraft(Long groupId, Long locationId, String sourceDescription,
                             HourlyPeakPattern pattern, ActuatorAction action) {
        try {
            // 이 경로는 ActuatorCommandRequest.of()를 안 거치고 Rule Engine flow 노드를 직접 조립하므로,
            // 다른 4개 경로(제안/예약/챗봇 즉시제어/관리자 수락)와 달리 여기서 따로 검증해야 한다.
            ActuatorCommandVocabulary.validate(action.actuatorType().name(), action.command(), action.commandValue());

            String cron = buildPreventiveCron(pattern.peakHour());
            String command = ActuatorCommandRequest.COMMAND_TO_CORE_STATE_KEY
                    .getOrDefault(action.command(), action.command());

            FlowDraftCreateRequest request = new FlowDraftCreateRequest(
                    locationId,
                    buildName(pattern),
                    buildDescription(pattern, sourceDescription),
                    List.of(
                            new FlowDraftNodeRequest("schedule", "SCHEDULE", Map.of("cron", cron)),
                            new FlowDraftNodeRequest("actuatorControl", "ACTUATOR_CONTROL", Map.of(
                                    "actuatorType", action.actuatorType().name(),
                                    "command", command,
                                    "commandValue", action.commandValue()))
                    ),
                    List.of(new FlowDraftLinkRequest("schedule", "actuatorControl", "out", "in"))
            );
            log.info("Rule Engine flow 초안 생성 요청 - locationId:{}, metric:{}, cron:{}, actuatorType:{}, command:{}, commandValue:{}",
                    locationId, pattern.metric(), cron, action.actuatorType(), command, action.commandValue());

            FlowDraftResponse response = ruleEngineClient.createAiDraft(groupId, request);

            if (response.replacedFlowId() != null) {
                log.info("기존 자동화를 대체했습니다 - flowId:{}, replacedFlowId:{}, locationId:{}, metric:{}",
                        response.flowId(), response.replacedFlowId(), locationId, pattern.metric());
            } else {
                log.info("Rule Engine flow 초안 생성 요청 완료 - flowId:{}, locationId:{}, metric:{}, status:{}",
                        response.flowId(), locationId, pattern.metric(), response.status());
            }
            return Optional.ofNullable(response.status());
        } catch (FeignException e) {
            log.warn("Rule Engine flow 초안 생성 요청 실패, 건너뜀 - locationId:{}, metric:{}, status:{}, body:{}",
                    locationId, pattern.metric(), e.status(), e.contentUTF8(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Rule Engine flow 초안 생성 요청 실패, 건너뜀 - locationId:{}, metric:{}",
                    locationId, pattern.metric(), e);
            return Optional.empty();
        }
    }

    /**
     * 피크 시간 15분 전에 미리 조작하도록 cron을 조립한다(예: 14시경 피크면 13:45). 자정을 넘어가는 경우(0시 피크 → 전날 23:45)엔 실행 요일도 하루
     * 앞당겨야 한다 - 그대로 MON-FRI를 쓰면 "월요일 23:45"가 화요일 0시 피크를 대상으로 하게 되어, 월요일 피크는 못 잡고 토요일 0시 피크를
     * 금요일 23:45에 잘못 잡아버린다(요일이 하루씩 밀림).
     */
    private String buildPreventiveCron(int peakHour) {
        int triggerHour = Math.floorMod(peakHour - PREVENTIVE_LEAD_HOURS, 24);
        boolean wrappedToPreviousDay = triggerHour > peakHour;
        String weekdays = wrappedToPreviousDay ? "SUN-THU" : "MON-FRI";
        return "0 45 " + triggerHour + " * * " + weekdays;
    }

    // "[AI] " 접두어는 Rule Engine의 FlowService.createAiDraft()가 강제한다(이 접두어가 없으면
    // InvalidAiDraftNameException) - AI가 만든 flow인지 이름만 보고 구분하려는 목적이라 여기서 빼면 안 됨.
    // 이름에 reportId 등 호출마다 달라지는 값은 안 넣는다 - 대신 결정론적인 시간대(timeSlot)를 붙인다.
    // 지표 하나에 자동화가 최대 1개라고 가정하면 안 되는 이유: 이 자동화가 성공해서 13시 피크를 없애면
    // 다음 분석 땐 15시가 새 최고점으로 잡히는데, 이름이 지표 하나로만 고정돼 있으면 15시 flow가 13시
    // flow를 갱신(archive)해버리고, 13시 문제는 다시 재발 → 13시가 다시 최고점 → 무한 반복(진동)에
    // 빠진다. 시간대를 이름에 넣어 "13시 자동화"와 "15시 자동화"가 서로 다른 flow로 공존하게 한다
    // (rule-engine-flow-duplicate-check-request §3 참고).
    private String buildName(HourlyPeakPattern pattern) {
        return "[AI] " + pattern.metric() + " 예방 자동화 (" + timeSlotLabel(pattern.peakHour()) + ")";
    }

    private String timeSlotLabel(int peakHour) {
        int offset = peakHour - BUSINESS_HOUR_START;
        int slotStart = BUSINESS_HOUR_START + (offset / TIME_SLOT_HOURS) * TIME_SLOT_HOURS;
        int slotEnd = Math.min(slotStart + TIME_SLOT_HOURS, BUSINESS_HOUR_END + 1);
        return slotStart + "~" + slotEnd + "시";
    }

    // sourceDescription: 어디서 이 판단이 나왔는지(리포트 제목+ID, 또는 "챗봇 요청" 등) 사람이 읽을 텍스트로만 남김
    private String buildDescription(HourlyPeakPattern pattern, String sourceDescription) {
        return String.format(Locale.ROOT,
                "[AI 자동 생성] %s 기준, 최근 데이터에서 %s이(가) %d시경 평균 %.1f로, 기간 평균(%.1f) 대비 %.1f%% 높게 "
                        + "반복 관측되어 미리 액추에이터를 가동하는 자동화를 제안합니다.",
                sourceDescription, pattern.metric(), pattern.peakHour(), pattern.peakValue(), pattern.baselineAvg(),
                pattern.percentAboveBaseline());
    }
}
