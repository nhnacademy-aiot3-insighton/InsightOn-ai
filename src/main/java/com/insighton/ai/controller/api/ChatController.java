package com.insighton.ai.controller.api;

import com.insighton.ai.controller.swagger.ChatApi;
import com.insighton.ai.domain.chatbot.dto.ChatRequest;
import com.insighton.ai.domain.chatbot.service.ChatbotService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController implements ChatApi {

    private final ChatbotService chatbotService;

    @Override
    @PostMapping
    public SseEmitter chat(@RequestParam Long groupId,
                           @RequestParam(required = false) Long locationId,
                           @RequestHeader("X-User-Id") Long userId,
                           @RequestBody ChatRequest request) {

        SseEmitter emitter = new SseEmitter(0L);

        Disposable subscription = chatbotService.streamChat(groupId, userId, locationId, request.message())
                .subscribe(
                        token -> {
                            try {
                                emitter.send(token);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(throwable -> subscription.dispose());

        return emitter;
    }
}
