package com.insighton.ai.domain.suggestion.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.insighton.ai.domain.suggestion.batch.SuggestionGenerationScheduler;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuggestionActionEventListenerTest {

    @Mock
    private SuggestionGenerationScheduler suggestionGenerationScheduler;

    @InjectMocks
    private SuggestionActionEventListener suggestionActionEventListener;

    private AiSuggestionActionEvent event() {
        return new AiSuggestionActionEvent(5L, 42L, 1L, "temperature", 30.0, OffsetDateTime.now());
    }

    @Test
    void handleSuggestionAction_수신한_이벤트를_그대로_스케줄러에_위임한다() {
        AiSuggestionActionEvent event = event();

        suggestionActionEventListener.handleSuggestionAction(event);

        verify(suggestionGenerationScheduler).generateEventTriggeredSuggestion(event);
    }

    @Test
    void handleSuggestionAction_스케줄러가_예외를_던지면_그대로_전파한다() {
        AiSuggestionActionEvent event = event();
        willThrow(new RuntimeException("제안 생성 실패"))
                .given(suggestionGenerationScheduler).generateEventTriggeredSuggestion(event);

        assertThatThrownBy(() -> suggestionActionEventListener.handleSuggestionAction(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("제안 생성 실패");
    }
}
