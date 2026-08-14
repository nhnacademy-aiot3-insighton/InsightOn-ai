package com.insighton.ai.domain.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.exception.ReportNotFoundException;
import com.insighton.ai.domain.report.repository.ReportRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private GroupAuthorizationService groupAuthorizationService;
    @Mock
    private Validator validator;
    @Mock
    private DashboardNotificationService dashboardNotificationService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private Report newReport(Long reportId, Long groupId, Long locationId, String title) {
        Report report = Report.builder()
                .groupId(groupId)
                .locationId(locationId)
                .title(title)
                .reportType(ReportType.WEEKLY)
                .content("본문")
                .build();
        ReflectionTestUtils.setField(report, "reportId", reportId);
        ReflectionTestUtils.setField(report, "createdAt", OffsetDateTime.now());
        return report;
    }

    @Test
    void findReports_기간_지정_안하면_기본값으로_조회한다() {
        Report report = newReport(1L, 5L, 42L, "8월 2주차 리포트");
        given(reportRepository.search(eq(5L), eq(42L), eq(ReportType.WEEKLY), any(), any(), eq(0), eq(20)))
                .willReturn(List.of(report));

        List<ReportListResponse> result =
                reportService.findReports(5L, 42L, ReportType.WEEKLY, null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reportId()).isEqualTo(1L);
        assertThat(result.get(0).title()).isEqualTo("8월 2주차 리포트");
    }

    @Test
    void findReports_기간_지정하면_그대로_사용한다() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(10);
        OffsetDateTime to = OffsetDateTime.now();
        given(reportRepository.search(5L, 42L, null, from, to, 0, 20)).willReturn(List.of());

        reportService.findReports(5L, 42L, null, from, to, 0, 20);

        verify(reportRepository).search(5L, 42L, null, from, to, 0, 20);
    }

    @Test
    void countReports_repository에_위임한다() {
        given(reportRepository.count(eq(5L), eq(42L), eq(ReportType.WEEKLY), any(), any())).willReturn(3L);

        long count = reportService.countReports(5L, 42L, ReportType.WEEKLY, null, null);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void findReport_성공() {
        Report report = newReport(1L, 5L, 42L, "리포트 제목");
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        ReportDetailResponse result = reportService.findReport(1L, 100L);

        assertThat(result.reportId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("리포트 제목");
        verify(groupAuthorizationService).requireMembership(5L, 100L);
    }

    @Test
    void findReport_리포트가_없으면_예외() {
        given(reportRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.findReport(999L, 100L))
                .isInstanceOf(ReportNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void findReport_그룹_멤버가_아니면_예외() {
        Report report = newReport(1L, 5L, 42L, "리포트 제목");
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(groupAuthorizationService.requireMembership(5L, 100L))
                .willThrow(new ForbiddenException("그룹 멤버가 아닙니다."));

        assertThatThrownBy(() -> reportService.findReport(1L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createReport_성공() {
        ReportCreateRequest request = new ReportCreateRequest(5L, 42L, "제목", ReportType.WEEKLY, "본문");
        given(validator.validate(request)).willReturn(Set.of());
        given(reportRepository.save(any(Report.class))).willAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "reportId", 10L);
            return saved;
        });

        Report result = reportService.createReport(request);

        assertThat(result.getReportId()).isEqualTo(10L);
        verify(dashboardNotificationService).create(new DashboardNotificationCreateRequest(
                5L, 42L, NotificationType.REPORT, 10L, "제목"));
    }

    @Test
    void createReport_검증_실패하면_예외를_던지고_저장하지_않는다() {
        ReportCreateRequest request = new ReportCreateRequest(null, 42L, "", ReportType.WEEKLY, "본문");
        ConstraintViolation<ReportCreateRequest> violation = mock(ConstraintViolation.class);
        given(validator.validate(request)).willReturn(Set.of(violation));

        assertThatThrownBy(() -> reportService.createReport(request))
                .isInstanceOf(ConstraintViolationException.class);

        verify(reportRepository, never()).save(any());
        verify(dashboardNotificationService, never()).create(any());
    }

    @Test
    void deleteByGroup_성공() {
        reportService.deleteByGroup(5L);

        verify(reportRepository).deleteByGroupId(5L);
    }

    @Test
    void deleteByGroup_groupId가_null이면_예외() {
        assertThatThrownBy(() -> reportService.deleteByGroup(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(reportRepository, never()).deleteByGroupId(any());
    }

    @Test
    void deleteByLocation_성공() {
        reportService.deleteByLocation(42L);

        verify(reportRepository).deleteByLocationId(42L);
    }

    @Test
    void deleteByLocation_locationId가_null이면_예외() {
        assertThatThrownBy(() -> reportService.deleteByLocation(null))
                .isInstanceOf(InvalidRequestException.class);

        verify(reportRepository, never()).deleteByLocationId(any());
    }
}