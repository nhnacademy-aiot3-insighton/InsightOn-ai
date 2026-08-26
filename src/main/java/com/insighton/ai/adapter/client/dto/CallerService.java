package com.insighton.ai.adapter.client.dto;

/**
 * Core의 액추에이터 제어 내부 API가 요구하는 callerService 값. Core의 ExecutedByType과
 * 이름이 정확히 같아야 한다(Jackson이 문자열 이름으로 매칭해서 역직렬화함) - 다만 Core 도메인
 * 타입을 AI가 직접 import하면 서비스 간 컴파일 결합이 생기므로 이 프로젝트 소유의 별도 타입으로 둔다.
 */
public enum CallerService {
    AI_SYSTEM
}
