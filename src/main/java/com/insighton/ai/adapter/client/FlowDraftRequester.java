package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.ActuatorCommandRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftCreateRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftLinkRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftNodeRequest;
import com.insighton.ai.adapter.client.dto.FlowDraftResponse;
import com.insighton.ai.domain.telemetrystats.dto.HourlyPeakPattern;
import feign.FeignException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final RuleEngineClient ruleEngineClient;

    public void requestDraft(Long groupId, Long locationId, Long reportId, String reportTitle,
                             HourlyPeakPattern pattern, ActuatorAction action) {
        try {
            String cron = buildPreventiveCron(pattern.peakHour());
            String command = ActuatorCommandRequest.COMMAND_TO_CORE_STATE_KEY
                    .getOrDefault(action.command(), action.command());

            FlowDraftCreateRequest request = new FlowDraftCreateRequest(
                    locationId,
                    buildName(pattern, reportTitle, reportId),
                    buildDescription(pattern, reportTitle),
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

            log.info("Rule Engine flow 초안 생성 요청 완료 - flowId:{}, locationId:{}, metric:{}, status:{}",
                    response.flowId(), locationId, pattern.metric(), response.status());
        } catch (FeignException e) {
            log.warn("Rule Engine flow 초안 생성 요청 실패, 건너뜀 - locationId:{}, metric:{}, status:{}, body:{}",
                    locationId, pattern.metric(), e.status(), e.contentUTF8(), e);
        } catch (Exception e) {
            log.warn("Rule Engine flow 초안 생성 요청 실패, 건너뜀 - locationId:{}, metric:{}",
                    locationId, pattern.metric(), e);
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
    // reportTitle(예: "8월 월간 3층 회의실 리포트")을 넣어 어느 리포트에서 나왔는지 한눈에 보이게 하고, reportId도 같이 붙여
    // 매 리포트마다 항상 새 초안을 만든다(같은 위치·지표라도 이번 판단이 지난달과 다를 수 있어서 예전 초안과 이름이 겹쳐 조용히
    // 재사용되면 안 됨 - reportTitle만으론 연도가 안 들어가 내년 같은 달에 또 겹칠 수 있어서 reportId가 실질적인 유일성 보장을 함).
    // 같은 리포트 내에서 재시도로 이 메서드가 두 번 불려도 reportId가 같아 이름도 같으므로, Rule Engine의
    // (group_id, location_id, name) 유니크 제약이 중복 생성만 막아준다 - 재시도 안전성은 그대로 유지.
    private String buildName(HourlyPeakPattern pattern, String reportTitle, Long reportId) {
        return "[AI] " + pattern.metric() + " 예방 자동화 (" + reportTitle + " #" + reportId + ")";
    }

    private String buildDescription(HourlyPeakPattern pattern, String reportTitle) {
        return String.format(Locale.ROOT,
                "[AI 자동 생성] %s 기준, 최근 리포트 기간 동안 %s이(가) %d시경 평균 %.1f로, 기간 평균(%.1f) 대비 %.1f%% 높게 "
                        + "반복 관측되어 미리 액추에이터를 가동하는 자동화를 제안합니다.",
                reportTitle, pattern.metric(), pattern.peakHour(), pattern.peakValue(), pattern.baselineAvg(),
                pattern.percentAboveBaseline());
    }
}
