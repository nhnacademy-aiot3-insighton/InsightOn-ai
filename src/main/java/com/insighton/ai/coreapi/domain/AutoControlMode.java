package com.insighton.ai.coreapi.domain;

/**
 * location별 AI 제안의 자동 실행 여부. SUGGESTION은 생성 시 대기(isAccepted=null) 상태로 저장해 사용자 수락을 기다리고, AI_DIRECT는 생성과 동시에 수락
 * 처리(isAccepted=true)하고 Core 제어 API를 즉시 호출.
 */
public enum AutoControlMode {
    SUGGESTION,
    AI_DIRECT
}