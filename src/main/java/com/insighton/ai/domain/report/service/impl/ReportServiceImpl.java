package com.insighton.ai.domain.report.service.impl;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.common.exception.InvalidRequestException;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.report.entity.Report;
import com.insighton.ai.domain.report.entity.ReportType;
import com.insighton.ai.domain.report.dto.ReportCreateRequest;
import com.insighton.ai.domain.report.dto.ReportDetailResponse;
import com.insighton.ai.domain.report.dto.ReportListResponse;
import com.insighton.ai.domain.report.exception.ReportNotFoundException;
import com.insighton.ai.domain.report.repository.ReportRepository;
import com.insighton.ai.domain.report.service.ReportService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 조회 및 생성 처리 담당 서비스 구현체.
 */
@RequiredArgsConstructor
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final GroupAuthorizationService groupAuthorizationService;
    private final Validator validator;
    private final DashboardNotificationService dashboardNotificationService;

    /**
     * 그룹 ID(필수), 위치 ID·리포트 종류(선택) 조건에 따른 리포트 목록 조회.
     *
     * @param groupId    그룹 ID(필수)
     * @param locationId 위치 ID(선택)
     * @param reportType 리포트 종류(선택)
     * @return 리포트 목록 응답
     */
    @Override
    public List<ReportListResponse> findReports(Long groupId, Long locationId, ReportType reportType) {

        return reportRepository.search(groupId, locationId, reportType).stream()
                .map(ReportListResponse::from)
                .toList();
    }

    /**
     * 리포트 ID 기준 단건 상세 조회.
     *
     * @param reportId 리포트 ID
     * @param userId   요청자 유저 ID
     * @return 리포트 상세 응답
     * @throws ReportNotFoundException 해당 ID의 리포트 미존재 시
     */
    @Override
    public ReportDetailResponse findReport(Long reportId, Long userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        groupAuthorizationService.requireMembership(report.getGroupId(), userId);

        log.info("리포트 조회 - reportId={}", reportId);
        return ReportDetailResponse.from(report);
    }

    /**
     * 리포트 신규 생성 및 저장.
     *
     * @param request 리포트 생성 요청
     * @return 저장된 리포트 엔티티
     * @throws ConstraintViolationException 요청값 검증 실패 시
     */
    @Transactional
    @Override
    public Report createReport(ReportCreateRequest request) {
        Set<ConstraintViolation<ReportCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        Report report = Report.builder()
                .groupId(request.groupId())
                .locationId(request.locationId())
                .title(request.title())
                .reportType(request.reportType())
                .content(request.content())
                .build();

        Report savedReport = reportRepository.save(report);
        log.info("리포트 생성 - reportId={}", savedReport.getReportId());

        dashboardNotificationService.create(new DashboardNotificationCreateRequest(
                savedReport.getGroupId(),
                savedReport.getLocationId(),
                NotificationType.REPORT,
                savedReport.getReportId(),
                savedReport.getTitle()
        ));

        return savedReport;
    }

    @Transactional
    @Override
    public void deleteByGroup(Long groupId) {
        if (groupId == null) {
            throw new InvalidRequestException("groupId는 필수값입니다.");
        }
        reportRepository.deleteByGroupId(groupId);
        log.info("리포트 일괄 삭제 - groupId:{}", groupId);
    }

    @Transactional
    @Override
    public void deleteByLocation(Long locationId) {
        if (locationId == null) {
            throw new InvalidRequestException("locationId는 필수값입니다.");
        }
        reportRepository.deleteByLocationId(locationId);
        log.info("리포트 일괄 삭제 - locationId:{}", locationId);
    }
}
