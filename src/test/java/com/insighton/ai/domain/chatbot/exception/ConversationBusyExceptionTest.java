package com.insighton.ai.domain.chatbot.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationBusyExceptionTest {

    @Test
    void 재시도_안내_메시지를_담고_있다() {
        ConversationBusyException exception = new ConversationBusyException();

        assertThat(exception.getMessage()).isEqualTo("이전 메시지를 아직 처리 중입니다. 잠시 후 다시 시도해주세요.");
    }
}
