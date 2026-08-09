package com.insighton.ai.domain.notification.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * groupId별로 열려있는 SSE 커넥션을 인스턴스 메모리에 관리하는 레지스트리
 */
@Component
public class SseEmitterRegistry {

    private final Map<Long, List<SseEmitter>> emittersByGroup = new ConcurrentHashMap<>();


    /**
     * groupId에 새 커넥션을 등록하고, 연결 종료/타임아웃/에러 시 자동으로 제거되도록 콜백
     *
     * @param groupId 그룹 ID
     * @param emitter 등록할 SSE 커넥션
     */
    public void sseRegister(Long groupId, SseEmitter emitter) {
        emittersByGroup.compute(groupId, (key, emitters) -> {
            List<SseEmitter> list = (emitters != null) ? emitters : new CopyOnWriteArrayList<>();
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> sseRemove(groupId, emitter));
        emitter.onTimeout(() -> sseRemove(groupId, emitter));
        emitter.onError(e -> sseRemove(groupId, emitter));
    }


    /**
     * groupId에서 특정 커넥션을 제거한다. 그룹에 남은 커넥션이 없으면 groupId 자체도 정리
     *
     * @param groupId 그룹 ID
     * @param emitter 제거할 SSE 커넥션
     */
    public void sseRemove(Long groupId, SseEmitter emitter) {
        emittersByGroup.computeIfPresent(groupId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /**
     * groupId에 연결된 모든 SSE 커넥션을 조회(없으면 빈 리스트)
     *
     * @param groupId 그룹 ID
     * @return 해당 그룹에 연결된 SSE 커넥션 목록
     */
    public List<SseEmitter> getEmitters(Long groupId) {
        return emittersByGroup.getOrDefault(groupId, List.of());
    }

    /**
     * 20초마다 모든 그룹의 열려있는 커넥션에 하트비트를 보내, 중간 인프라(Gateway/로드밸런서)가 유휴 연결로 판단해서 끊어버리는 걸 방지 전송 실패한 커넥션은 죽은 것으로 보고 정리
     */
    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        emittersByGroup.forEach((groupId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    sseRemove(groupId, emitter);
                }
            }
        });
    }
}
