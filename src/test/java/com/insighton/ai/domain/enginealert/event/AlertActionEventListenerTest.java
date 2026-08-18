package com.insighton.ai.domain.enginealert.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.insighton.ai.domain.enginealert.entity.Severity;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertActionEventListenerTest {

    @Mock
    private EngineAlertService engineAlertService;

    @InjectMocks
    private AlertActionEventListener alertActionEventListener;

    private EngineAlertActionEvent event() {
        return new EngineAlertActionEvent("event-1", 5L, 42L, 7L, "제목", "메시지", Severity.CRITICAL,
                Map.of("temperature", 29.5), OffsetDateTime.now());
    }

    @Test
    void handleEngineAlertAction_수신한_이벤트를_그대로_서비스에_위임한다() {
        EngineAlertActionEvent event = event();

        alertActionEventListener.handleEngineAlertAction(event);

        verify(engineAlertService).createEngineAlert(event);
    }

    @Test
    void handleEngineAlertAction_서비스가_예외를_던지면_그대로_전파한다() {
        EngineAlertActionEvent event = event();
        willThrow(new RuntimeException("알람 생성 실패")).given(engineAlertService).createEngineAlert(event);

        assertThatThrownBy(() -> alertActionEventListener.handleEngineAlertAction(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("알람 생성 실패");
    }
}
