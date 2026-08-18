package com.insighton.ai.domain.suggestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.dto.ActionPayload;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogCreateRequest;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.dto.SuggestionSummary;
import com.insighton.ai.domain.suggestion.entity.SuggestionLog;
import com.insighton.ai.domain.suggestion.exception.SuggestionLogNotFoundException;
import com.insighton.ai.domain.suggestion.repository.SuggestionLogRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SuggestionLogServiceImplTest {

    @Mock
    private SuggestionLogRepository suggestionLogRepository;
    @Mock
    private Validator validator;
    @Mock
    private GroupAuthorizationService groupAuthorizationService;
    @Mock
    private DashboardNotificationService dashboardNotificationService;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private SuggestionLogServiceImpl suggestionLogService;

    private SuggestionLog newSuggestion(Long id, Long groupId, Long locationId, String actionPayload,
                                        Boolean isAccepted) {
        SuggestionLog suggestion = SuggestionLog.builder()
                .groupId(groupId)
                .locationId(locationId)
                .title("제안 제목")
                .suggestionText("제안 문구")
                .actionPayload(actionPayload)
                .isAccepted(isAccepted)
                .build();
        ReflectionTestUtils.setField(suggestion, "suggestionLogId", id);
        ReflectionTestUtils.setField(suggestion, "createdAt", OffsetDateTime.now());
        return suggestion;
    }

    @Test
    void findSuggestionLogs_성공() {
        Pageable pageable = PageRequest.of(0, 20);
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.search(eq(5L), eq(42L), any(), any(), eq(pageable)))
                .willReturn(List.of(suggestion));

        List<SuggestionLogResponse> result =
                suggestionLogService.findSuggestionLogs(5L, 42L, null, null, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).suggestionLogId()).isEqualTo(1L);
    }

    @Test
    void findSuggestionLogs_groupId가_null이면_예외() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> suggestionLogService.findSuggestionLogs(null, 42L, null, null, pageable))
                .isInstanceOf(InvalidRequestException.class);

        verify(suggestionLogRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void countSuggestionLogs_성공() {
        given(suggestionLogRepository.count(eq(5L), eq(42L), any(), any())).willReturn(4L);

        long count = suggestionLogService.countSuggestionLogs(5L, 42L, null, null);

        assertThat(count).isEqualTo(4L);
    }

    @Test
    void countSuggestionLogs_groupId가_null이면_예외() {
        assertThatThrownBy(() -> suggestionLogService.countSuggestionLogs(null, 42L, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void findSuggestionLog_성공() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));

        SuggestionLogResponse result = suggestionLogService.findSuggestionLog(1L, 100L);

        assertThat(result.suggestionLogId()).isEqualTo(1L);
        verify(groupAuthorizationService).requireMembership(5L, 100L);
    }

    @Test
    void findSuggestionLog_없으면_예외() {
        given(suggestionLogRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionLogService.findSuggestionLog(999L, 100L))
                .isInstanceOf(SuggestionLogNotFoundException.class);
    }

    @Test
    void findSuggestionLog_그룹_멤버가_아니면_예외() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        given(groupAuthorizationService.requireMembership(5L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        assertThatThrownBy(() -> suggestionLogService.findSuggestionLog(1L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_성공() {
        SuggestionLogCreateRequest request = new SuggestionLogCreateRequest(5L, 42L, "제목", "문구", "{}", null);
        given(validator.validate(request)).willReturn(Set.of());
        given(suggestionLogRepository.save(any(SuggestionLog.class))).willAnswer(invocation -> {
            SuggestionLog saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "suggestionLogId", 10L);
            return saved;
        });

        SuggestionLogResponse result = suggestionLogService.create(request);

        assertThat(result.suggestionLogId()).isEqualTo(10L);
        verify(dashboardNotificationService).create(new DashboardNotificationCreateRequest(
                5L, 42L, NotificationType.SUGGESTION, 10L, "제목"));
    }

    @Test
    void create_검증_실패하면_예외를_던지고_저장하지_않는다() {
        SuggestionLogCreateRequest request = new SuggestionLogCreateRequest(null, 42L, "", "", "", null);
        ConstraintViolation<SuggestionLogCreateRequest> violation = mock(ConstraintViolation.class);
        given(validator.validate(request)).willReturn(Set.of(violation));

        assertThatThrownBy(() -> suggestionLogService.create(request))
                .isInstanceOf(ConstraintViolationException.class);

        verify(suggestionLogRepository, never()).save(any());
        verify(dashboardNotificationService, never()).create(any());
    }

    @Test
    void accept_액추에이터_명령이_있으면_Core를_호출한다() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{\"actuatorType\":\"AIRCON\"}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        ActionPayload actionPayload = new ActionPayload(42L, "AIRCON", "POWER_STATUS", "ON");
        given(jsonMapper.readValue(anyString(), eq(ActionPayload.class))).willReturn(actionPayload);

        SuggestionLogResponse result = suggestionLogService.accept(1L, 100L);

        assertThat(result.isAccepted()).isTrue();
        verify(coreClient).executeActuatorCommand(actionPayload);
    }

    @Test
    void accept_액추에이터_명령이_없으면_Core를_호출하지_않는다() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        ActionPayload actionPayload = new ActionPayload(42L, null, null, null);
        given(jsonMapper.readValue(anyString(), eq(ActionPayload.class))).willReturn(actionPayload);

        SuggestionLogResponse result = suggestionLogService.accept(1L, 100L);

        assertThat(result.isAccepted()).isTrue();
        verify(coreClient, never()).executeActuatorCommand(any());
    }

    @Test
    void accept_actionPayload_파싱_실패하면_예외() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "잘못된 JSON", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        given(jsonMapper.readValue(anyString(), eq(ActionPayload.class)))
                .willThrow(new RuntimeException("파싱 실패"));

        assertThatThrownBy(() -> suggestionLogService.accept(1L, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(coreClient, never()).executeActuatorCommand(any());
    }

    @Test
    void accept_없으면_예외() {
        given(suggestionLogRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionLogService.accept(999L, 100L))
                .isInstanceOf(SuggestionLogNotFoundException.class);
    }

    @Test
    void accept_그룹_멤버가_아니면_예외() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        given(groupAuthorizationService.requireMembership(5L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        assertThatThrownBy(() -> suggestionLogService.accept(1L, 100L))
                .isInstanceOf(ForbiddenException.class);

        verify(coreClient, never()).executeActuatorCommand(any());
    }

    @Test
    void reject_성공() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));

        SuggestionLogResponse result = suggestionLogService.reject(1L, 100L);

        assertThat(result.isAccepted()).isFalse();
    }

    @Test
    void reject_없으면_예외() {
        given(suggestionLogRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionLogService.reject(999L, 100L))
                .isInstanceOf(SuggestionLogNotFoundException.class);
    }

    @Test
    void reject_그룹_멤버가_아니면_예외() {
        SuggestionLog suggestion = newSuggestion(1L, 5L, 42L, "{}", null);
        given(suggestionLogRepository.findById(1L)).willReturn(Optional.of(suggestion));
        given(groupAuthorizationService.requireMembership(5L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        assertThatThrownBy(() -> suggestionLogService.reject(1L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteByGroup_성공() {
        suggestionLogService.deleteByGroup(5L);

        verify(suggestionLogRepository).deleteByGroupId(5L);
    }

    @Test
    void deleteByGroup_groupId가_null이면_예외() {
        assertThatThrownBy(() -> suggestionLogService.deleteByGroup(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(suggestionLogRepository, never()).deleteByGroupId(any());
    }

    @Test
    void deleteByLocation_성공() {
        suggestionLogService.deleteByLocation(42L);

        verify(suggestionLogRepository).deleteByLocationId(42L);
    }

    @Test
    void deleteByLocation_locationId가_null이면_예외() {
        assertThatThrownBy(() -> suggestionLogService.deleteByLocation(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(suggestionLogRepository, never()).deleteByLocationId(any());
    }

    @Test
    void summarizePeriod_수락_거절_대기_건수를_집계한다() {
        List<SuggestionLog> suggestions = List.of(
                newSuggestion(1L, 5L, 42L, "{}", true),
                newSuggestion(2L, 5L, 42L, "{}", true),
                newSuggestion(3L, 5L, 42L, "{}", false),
                newSuggestion(4L, 5L, 42L, "{}", null)
        );
        given(suggestionLogRepository.searchByPeriod(eq(42L), any(), any())).willReturn(suggestions);

        SuggestionSummary summary = suggestionLogService.summarizePeriod(42L, OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now());

        assertThat(summary.totalCount()).isEqualTo(4);
        assertThat(summary.acceptedCount()).isEqualTo(2);
        assertThat(summary.rejectedCount()).isEqualTo(1);
        assertThat(summary.pendingCount()).isEqualTo(1);
    }
}