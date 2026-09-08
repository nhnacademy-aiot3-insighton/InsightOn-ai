package com.insighton.ai.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(GoogleGenAiChatModel chatModel) {
        // includeThoughts를 명시적으로 false로 안 두면, Gemini가 반환하는 사고 과정(thought) 파트를
        // Spring AI가 isThought 메타데이터만 붙이고 본문 텍스트에서는 걸러내지 않아 .content()/.entity()
        // 결과에 그대로 섞여 나온다(리포트 마크다운/제안 JSON 파싱이 깨질 수 있는 원인).
        return ChatClient.builder(chatModel)
                .defaultOptions(GoogleGenAiChatOptions.builder().includeThoughts(false))
                .build();
    }
}