package com.insighton.ai.domain.notification.sse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.insighton.ai.domain.notification.dto.DashboardNotificationBroadcastEvent;
import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.NotificationType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class DashboardNotificationBroadcastListenerTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private DashboardNotificationBroadcastListener listener;

    @BeforeEach
    void setUp() {
        listener = new DashboardNotificationBroadcastListener(sseEmitterRegistry);
    }

    @Test
    void handleBroadcast_그룹의_모든_커넥션에_알림을_전송한다() throws IOException {
        SseEmitter emitter1 = org.mockito.Mockito.mock(SseEmitter.class);
        SseEmitter emitter2 = org.mockito.Mockito.mock(SseEmitter.class);
        given(sseEmitterRegistry.getEmitters(5L)).willReturn(List.of(emitter1, emitter2));
        DashboardNotificationBroadcastEvent event = new DashboardNotificationBroadcastEvent(5L, notification());

        listener.handleBroadcast(event);

        verify(emitter1, times(1)).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(1)).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        verify(sseEmitterRegistry, never()).sseRemove(eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleBroadcast_전송_실패한_커넥션은_레지스트리에서_제거한다() throws IOException {
        SseEmitter deadEmitter = org.mockito.Mockito.mock(SseEmitter.class);
        given(sseEmitterRegistry.getEmitters(5L)).willReturn(List.of(deadEmitter));
        willThrow(new IOException("connection reset")).given(deadEmitter)
                .send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        DashboardNotificationBroadcastEvent event = new DashboardNotificationBroadcastEvent(5L, notification());

        listener.handleBroadcast(event);

        verify(sseEmitterRegistry).sseRemove(5L, deadEmitter);
    }

    private DashboardNotificationResponse notification() {
        return new DashboardNotificationResponse(1L, 10L, NotificationType.GATEWAY, 1L, "제목", false,
                OffsetDateTime.now());
    }
}
