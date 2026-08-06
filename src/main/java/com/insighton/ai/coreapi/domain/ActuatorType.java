package com.insighton.ai.coreapi.domain;

/**
 * Core가 관리하는 액추에이터 종류. 제안 생성 시 LLM 출력(SuggestionDraft.actuatorType)과 조작 가능 명령 목록
 * (SuggestionGenerationScheduler.ACTUATOR_COMMANDS) 검증에 쓰임 — Core actuators.actuator_type과 이름으로 매칭.
 */
public enum ActuatorType {
    AIRCON,
    AIR_PURIFIER,
    VENTILATION_FAN
}