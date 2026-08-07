package com.insighton.ai.coreapi.domain;

/**
 * 액추에이터 조작 주체. Core의 actuator_run_logs.executed_by_type과 이름으로 매칭된다.
 */
public enum ExecutedByType {
    USER,
    AI_SYSTEM,
    RULE_ENGINE
}
