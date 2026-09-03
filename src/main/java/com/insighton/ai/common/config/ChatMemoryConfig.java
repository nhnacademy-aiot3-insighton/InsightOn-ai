package com.insighton.ai.common.config;

import com.insighton.ai.adapter.client.tool.ActuatorChatTool;
import com.insighton.ai.adapter.client.tool.LocationChatTool;
import com.insighton.ai.adapter.client.tool.WeatherChatTool;
import com.insighton.ai.domain.enginealert.tool.EngineAlertChatTool;
import com.insighton.ai.domain.flow.tool.FlowRecommendationChatTool;
import com.insighton.ai.domain.notification.tool.NotificationChatTool;
import com.insighton.ai.domain.report.tool.ReportChatTool;
import com.insighton.ai.domain.scheduledtask.tool.ScheduledComfortSetupChatTool;
import com.insighton.ai.domain.suggestion.tool.SuggestionChatTool;
import com.insighton.ai.domain.telemetrystats.tool.TelemetryStatChatTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 챗봇 전용 ChatMemory/ChatClient 설정. {@link ChatClientConfig#chatClient}는 report/suggestion 배치가 CONVERSATION_ID 없이 호출 중이라,
 * MessageChatMemoryAdvisor를 그 빈에 붙이면 기존 호출이 전부 예외를 던진다 — 그래서 챗봇용 ChatClient를 별도 빈({@link #chatbotClient})으로 분리한다.
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatbotClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory,
                                    ReportChatTool reportChatTool,
                                    TelemetryStatChatTool telemetryStatChatTool,
                                    EngineAlertChatTool engineAlertChatTool,
                                    SuggestionChatTool suggestionChatTool,
                                    NotificationChatTool notificationChatTool,
                                    LocationChatTool locationChatTool,
                                    ActuatorChatTool actuatorChatTool,
                                    WeatherChatTool weatherChatTool,
                                    ScheduledComfortSetupChatTool scheduledComfortSetupChatTool,
                                    FlowRecommendationChatTool flowRecommendationChatTool) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(reportChatTool, telemetryStatChatTool,
                        engineAlertChatTool, suggestionChatTool,
                        notificationChatTool, locationChatTool,
                        actuatorChatTool, weatherChatTool,
                        scheduledComfortSetupChatTool, flowRecommendationChatTool)
                .build();
    }
}
