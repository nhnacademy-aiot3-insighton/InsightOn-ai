package com.insighton.ai.domain.chatbot.exception;

public class ConversationBusyException extends RuntimeException {

    public ConversationBusyException() {
        super("이전 메시지를 아직 처리 중입니다. 잠시 후 다시 시도해주세요.");
    }
}
