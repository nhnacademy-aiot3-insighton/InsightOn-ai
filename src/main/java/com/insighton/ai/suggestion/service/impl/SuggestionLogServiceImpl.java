package com.insighton.ai.suggestion.service.impl;

import com.insighton.ai.coreapi.client.CoreClient;
import com.insighton.ai.coreapi.domain.ExecutedByType;
import com.insighton.ai.coreapi.dto.ActionPayload;
import com.insighton.ai.coreapi.dto.ActuatorCommandRequest;
import com.insighton.ai.coreapi.service.GroupAuthorizationService;
import com.insighton.ai.exception.InvalidRequestException;
import com.insighton.ai.notification.domain.NotificationType;
import com.insighton.ai.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.notification.service.DashboardNotificationService;
import com.insighton.ai.suggestion.domain.SuggestionLog;
import com.insighton.ai.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.suggestion.dto.SuggestionSummary;
import com.insighton.ai.suggestion.exception.SuggestionLogNotFoundException;
import com.insighton.ai.suggestion.repository.SuggestionLogRepository;
import com.insighton.ai.suggestion.service.SuggestionLogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 제안 로그 조회·생성·수락/거절 처리 담당 서비스 구현체.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SuggestionLogServiceImpl implements SuggestionLogService {

    private static final ExecutedByType CALLER_SERVICE = ExecutedByType.AI_SYSTEM;

    private final SuggestionLogRepository suggestionLogRepository;
    private final Validator validator;
    private final GroupAuthorizationService groupAuthorizationService;
    private final DashboardNotificationService dashboardNotificationService;
    private final JsonMapper jsonMapper;
    private final CoreClient coreClient;

    /**
     * 그룹 ID(필수), 위치 ID(선택) 조건에 따른 제안 로그 목록 조회.
     *
     * @param groupId    그룹 ID(필수)
     * @param locationId 위치 ID(선택)
     * @return 제안 로그 목록 응답
     * @throws InvalidRequestException groupId가 null인 경우
     */
    @Override
    public List<SuggestionLogResponse> findSuggestionLogs(Long groupId, Long locationId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId must not be null");
        }

        List<SuggestionLog> suggestions = suggestionLogRepository.search(groupId, locationId);
        log.info("SuggestionLog 리스트 조회 - groupId: {}, locationId:{}, log size:{}", groupId, locationId,
                suggestions.size());

        return suggestions.stream()
                .map(SuggestionLogResponse::from)
                .toList();
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
     * AI 제안 수락 처리(is_accepted=true). actionPayload에 담긴 액추에이터 명령들을 순서대로 Core 제어 API에 전달해 즉시 실행한다. 순수 조언(창문 개방 등)만 있던 제안은
     * actionPayload가 빈 배열이라 실행할 게 없어 수락 처리만 된다.
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

        groupAuthorizationService.requireMembership(suggestionLog.getGroupId(), userId);

        suggestionLog.changeAccepted(true);

        List<ActionPayload> actionPayloads = parseActionPayloads(suggestionLog.getActionPayload());
        // TODO: Core 액추에이터 명령/값 API 확정되면 요청/응답 형식 재검토
        actionPayloads.forEach(payload ->
                coreClient.executeActuatorCommand(suggestionLog.getLocationId(),
                        new ActuatorCommandRequest(payload.actuatorType(), payload.command(), payload.commandValue(),
                                CALLER_SERVICE)));

        log.info("AI 제안 수락 - suggestionLogId:{}, 액션 수:{}", suggestionLogId, actionPayloads.size());
        return SuggestionLogResponse.from(suggestionLog);
    }

    private List<ActionPayload> parseActionPayloads(String actionPayloadJson) {
        try {
            return jsonMapper.readValue(actionPayloadJson, new TypeReference<List<ActionPayload>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("액추에이터 명령 JSON 파싱 실패: " + actionPayloadJson, e);
        }
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

        groupAuthorizationService.requireMembership(suggestionLog.getGroupId(), userId);

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
}
