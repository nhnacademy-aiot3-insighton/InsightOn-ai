package com.insighton.ai.domain.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void sseRegister하면_getEmitters로_조회된다() {
        SseEmitter emitter = mock(SseEmitter.class);

        registry.sseRegister(5L, emitter);

        assertThat(registry.getEmitters(5L)).containsExactly(emitter);
    }

    @Test
    void 등록되지_않은_그룹은_빈_목록을_반환한다() {
        assertThat(registry.getEmitters(999L)).isEmpty();
    }

    @Test
    void sseRemove하면_목록에서_사라지고_그룹_자체도_정리된다() {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.sseRegister(5L, emitter);

        registry.sseRemove(5L, emitter);

        assertThat(registry.getEmitters(5L)).isEmpty();
    }

    @Test
    void sendHeartbeat_모든_커넥션에_코멘트를_전송한다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.sseRegister(5L, emitter);

        registry.sendHeartbeat();

        verify(emitter).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendHeartbeat_IOException이_나면_해당_커넥션을_제거한다() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        willThrow(new IOException("연결 끊김")).given(deadEmitter)
                .send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        registry.sseRegister(5L, deadEmitter);

        registry.sendHeartbeat();

        assertThat(registry.getEmitters(5L)).isEmpty();
    }

    @Test
    void sendHeartbeat_IllegalStateException이_나도_해당_커넥션을_제거한다() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        willThrow(new IllegalStateException("이미 완료됨")).given(deadEmitter)
                .send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        registry.sseRegister(5L, deadEmitter);

        registry.sendHeartbeat();

        assertThat(registry.getEmitters(5L)).isEmpty();
    }
}
