package com.insighton.ai.domain.scheduledtask.dto;

/**
 * 사용자가 채팅으로 요청한 1회성 예약("n시까지 쾌적하게 해놔"). Rule Engine flow(반복 자동화)가 아니라 Redis
 * ZSET({@value #REDIS_KEY}, score=triggerAt epoch초)에만 저장되는 일회성 작업이라 DB 엔티티가 없다.
 */
public record ScheduledActuatorTask(
        String taskId,
        Long groupId,
        Long locationId,
        String purposeText,
        int attemptCount
) {
    public static final String REDIS_KEY = "scheduled-actuator-task";
}
