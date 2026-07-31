package com.insighton.ai.enginealert.repository;

import com.insighton.ai.enginealert.domain.EngineAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineAlertRepository extends JpaRepository<EngineAlert, Long>, EngineAlertQueryRepository {

    void deleteByGroupId(Long groupId);

    void deleteByLocationId(Long locationId);
}
