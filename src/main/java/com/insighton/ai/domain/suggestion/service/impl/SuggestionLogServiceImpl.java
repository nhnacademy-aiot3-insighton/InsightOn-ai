package com.insighton.ai.domain.suggestion.service.impl;

import com.insighton.ai.adapter.client.ActuatorCommandExecutor;
import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.dto.ActionPayload;
import com.insighton.ai.adapter.client.dto.ActuatorAction;
import com.insighton.ai.adapter.client.dto.CallerService;
import com.insighton.ai.adapter.client.dto.GroupRole;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.suggestion.dto.RejectionPattern;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.entity.SuggestionLog;
import com.insighton.ai.domain.suggestion.exception.SuggestionLogNotFoundException;
import com.insighton.ai.domain.suggestion.repository.SuggestionLogRepository;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 제안 로그 조회·생성·수락/거절 처리 담당 서비스 구현체.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SuggestionLogServiceImpl implements SuggestionLogService {

    private final SuggestionLogRepository suggestionLogRepository;
    private final Validator validator;
    private final GroupAuthorizationService groupAuthorizationService;
    private final DashboardNotificationService dashboardNotificationService;
    private final JsonMapper jsonMapper;
    private final ActuatorCommandExecutor actuatorCommandExecutor;

    private static final int REJECTION_PATTERN_LOOKBACK = 30;
    private static final int REJECTION_PATTERN_MIN_SAMPLES = 8;
    private static final double REJECTION_PATTERN_MIN_RATE = 0.7;

    /**
     * 그룹 ID(필수), 위치 ID(선택) 조건에 따른 제안 로그 목록 조회.
     *
     * @param groupId    그룹 ID(필수)
     * @param locationId 위치 ID(선택)
     * @return 제안 로그 목록 응답
     * @throws InvalidRequestException groupId가 null인 경우
     */
    @Override
    public List<SuggestionLogResponse> findSuggestionLogs(Long groupId, Long locationId, OffsetDateTime from,
                                                          OffsetDateTime to, Pageable pageable) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId must not be null");
        }

        List<SuggestionLog> suggestions = suggestionLogRepository.search(groupId, locationId, resolveFrom(from),
                resolveTo(to), pageable);
        log.info("SuggestionLog 리스트 조회 - groupId: {}, locationId:{}, log size:{}", groupId, locationId,
                suggestions.size());

        return suggestions.stream()
                .map(SuggestionLogResponse::from)
                .toList();
    }

    @Override
    public long countSuggestionLogs(Long groupId, Long locationId, OffsetDateTime from, OffsetDateTime to) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId must not be null");
        }
        return suggestionLogRepository.count(groupId, locationId, resolveFrom(from), resolveTo(to));
    }

    private OffsetDateTime resolveFrom(OffsetDateTime from) {
        return from != null ? from : OffsetDateTime.now().minusMonths(1);
    }

    private OffsetDateTime resolveTo(OffsetDateTime to) {
        return to != null ? to : OffsetDateTime.now();
    }

    /**
     * 제안 로그 ID 기준 단건 상세 조회.
     *
     * @param suggestionLogId 제안 로그 ID
     * @param userId          요청자 유저 ID
     * @return 제안 로그 응답
     * @throws SuggestionLogNotFoundException 해당 ID의 제안 로그 미존재 시
     */
    @Override
    public SuggestionLogResponse findSuggestionLog(Long suggestionLogId, Long userId) {
        SuggestionLog suggestionLog = suggestionLogRepository.findById(suggestionLogId)
                .orElseThrow(() -> new SuggestionLogNotFoundException(suggestionLogId));

        groupAuthorizationService.requireMembership(suggestionLog.getGroupId(), userId);

        log.info("SuggestionLog 조회 - suggestionLogId:{}", suggestionLogId);
        return SuggestionLogResponse.from(suggestionLog);
    }

    /**
     * 제안 로그 신규 생성, 저장 전 Bean Validation 기반 요청값 유효성 검증 수행.
     *
     * @param request 제안 로그 생성 요청
     * @return 저장된 제안 로그 응답
     * @throws ConstraintViolationException 요청값 검증 실패 시
     */
    @Transactional
    @Override
    public SuggestionLogResponse create(SuggestionLogCreateRequest request) {
        Set<ConstraintViolation<SuggestionLogCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        SuggestionLog suggestionLog = SuggestionLog.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .title(request.title())
                .suggestionText(request.suggestionText())
                .actionPayload(request.actionPayload())
                .isAccepted(request.isAccepted())
                .build();

        SuggestionLog savedSuggestionLog = suggestionLogRepository.save(suggestionLog);

        log.info("SuggestionLog 저장 - suggestionLogId:{}", savedSuggestionLog.getSuggestionLogId());

        dashboardNotificationService.create(new DashboardNotificationCreateRequest(
                savedSuggestionLog.getGroupId(),
                savedSuggestionLog.getLocationId(),
                NotificationType.SUGGESTION,
                savedSuggestionLog.getSuggestionLogId(),
                savedSuggestionLog.getTitle()
        ));

        return SuggestionLogResponse.from(savedSuggestionLog);
    }

    /**
     * AI 제안 수락 처리(is_accepted=true). actionPayload에 실제 액추에이터 명령이 담겨있으면 Core 제어 API를 호출해 즉시 실행하고, 창문 개방처럼 조언만 있는
     * 제안(actuatorType 없음)이면 수락 처리만 하고 Core 호출은 생략한다.
     *
     * @param suggestionLogId 제안 로그 ID
     * @param userId          요청자 유저 ID
     * @return 수락 처리된 제안 로그 응답
     * @throws SuggestionLogNotFoundException 해당 ID의 제안 로그 미존재 시
     */
    @Transactional
    @Override
    public SuggestionLogResponse accept(Long suggestionLogId, Long userId) {
        SuggestionLog suggestionLog = suggestionLogRepository.findById(suggestionLogId)
                .orElseThrow(() -> new SuggestionLogNotFoundException(suggestionLogId));

        groupAuthorizationService.requireRole(suggestionLog.getGroupId(), userId, GroupRole.MANAGER);

        suggestionLog.changeAccepted(true);

        ActionPayload actionPayload = parseActionPayload(suggestionLog.getActionPayload());

        if (!actionPayload.actions().isEmpty()) {
            actuatorCommandExecutor.execute(actionPayload.locationId(), actionPayload.actions(),
                    CallerService.AI_SYSTEM);
        }

        log.info("AI 제안 수락 - suggestionLogId:{}", suggestionLogId);
        return SuggestionLogResponse.from(suggestionLog);
    }

    /**
     * AI 제안 거절 처리(is_accepted=false).
     *
     * @param suggestionLogId 제안 로그 ID
     * @param userId          요청자 유저 ID
     * @return 거절 처리된 제안 로그 응답
     * @throws SuggestionLogNotFoundException 해당 ID의 제안 로그 미존재 시
     */
    @Transactional
    @Override
    public SuggestionLogResponse reject(Long suggestionLogId, Long userId) {
        SuggestionLog suggestionLog = suggestionLogRepository.findById(suggestionLogId)
                .orElseThrow(() -> new SuggestionLogNotFoundException(suggestionLogId));

        groupAuthorizationService.requireRole(suggestionLog.getGroupId(), userId, GroupRole.MANAGER);

        log.info("AI 제안 거절 - suggestionLogId:{}", suggestionLogId);

        suggestionLog.changeAccepted(false);
        return SuggestionLogResponse.from(suggestionLog);
    }

    @Transactional
    @Override
    public void deleteByGroup(Long groupId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }
        suggestionLogRepository.deleteByGroupId(groupId);
        log.info("제안 로그 일괄 삭제 - groupId:{}", groupId);
    }

    @Transactional
    @Override
    public void deleteByLocation(Long locationId) {
        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }
        suggestionLogRepository.deleteByLocationId(locationId);
        log.info("제안 로그 일괄 삭제 - locationId:{}", locationId);
    }

    @Override
    public SuggestionSummary summarizePeriod(Long locationId, OffsetDateTime from, OffsetDateTime to) {
        List<SuggestionLog> suggestions = suggestionLogRepository.searchByPeriod(locationId, from, to);

        long totalCount = suggestions.size();
        long acceptedCount = suggestions.stream().filter(s ->
                Boolean.TRUE.equals(s.getIsAccepted())).count();

        long rejectedCount = suggestions.stream().filter(s ->
                Boolean.FALSE.equals(s.getIsAccepted())).count();

        long pendingCount = suggestions.stream().filter(s ->
                s.getIsAccepted() == null).count();

        return new SuggestionSummary(totalCount, acceptedCount, rejectedCount, pendingCount);
    }

    @Override
    public List<RejectionPattern> findRejectionPatterns(Long locationId) {
        List<SuggestionLog> recent = suggestionLogRepository.findByLocationIdAndIsAcceptedNotNullOrderByCreatedAtDesc(
                locationId,
                PageRequest.of(0, REJECTION_PATTERN_LOOKBACK));

        Map<List<String>, long[]> stats = new LinkedHashMap<>();

        for (SuggestionLog suggestion : recent) {
            ActionPayload payload = parseActionPayload(suggestion.getActionPayload());

            for (ActuatorAction action : payload.actions()) {
                if ("SET_TEMPERATURE".equals(action.command())) {
                    continue;
                }
                List<String> key = List.of(action.actuatorType().name(), action.command(), action.commandValue());

                long[] counter = stats.computeIfAbsent(key, k -> new long[2]);
                counter[1]++;

                if (Boolean.FALSE.equals(suggestion.getIsAccepted())) {
                    counter[0]++;
                }
            }
        }

        return stats.entrySet().stream()
                .filter(e -> e.getValue()[1] >= REJECTION_PATTERN_MIN_SAMPLES
                        && (double) e.getValue()[0] / e.getValue()[1] >= REJECTION_PATTERN_MIN_RATE)
                .map(e -> new RejectionPattern(e.getKey().get(0), e.getKey().get(1), e.getKey().get(2),
                        e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private ActionPayload parseActionPayload(String actionPayloadJson) {
        try {
            return jsonMapper.readValue(actionPayloadJson, ActionPayload.class);

        } catch (Exception e) {
            throw new IllegalArgumentException("액추에이터 명령 JSON 파싱 실패: " + actionPayloadJson, e);
        }
    }
}
