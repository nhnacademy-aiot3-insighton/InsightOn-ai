package com.insighton.ai.report.repository;

import com.insighton.ai.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long>, ReportQueryRepository {

    @Modifying
    @Query("delete from Report r where r.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("delete from Report r where r.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Long locationId);
}
