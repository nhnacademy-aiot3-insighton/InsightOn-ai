package com.insighton.ai.common.exception;

import com.insighton.ai.adapter.client.exception.ActuatorNotFoundException;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.adapter.client.exception.InvalidActuatorCommandException;
import com.insighton.ai.domain.chatbot.exception.ConversationBusyException;
import com.insighton.ai.domain.enginealert.exception.EngineAlertNotFoundException;
import com.insighton.ai.domain.suggestion.exception.SuggestionAlreadyProcessedException;
import com.insighton.ai.domain.notification.exception.DashboardNotificationNotFoundException;
import com.insighton.ai.domain.report.exception.ReportNotFoundException;
import com.insighton.ai.domain.suggestion.exception.SuggestionLogNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ReportNotFoundException.class,
            SuggestionLogNotFoundException.class,
            DashboardNotificationNotFoundException.class,
            EngineAlertNotFoundException.class,
            ActuatorNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(SuggestionAlreadyProcessedException.class)
    public ResponseEntity<ErrorResponse> handleSuggestionAlreadyProcessed(SuggestionAlreadyProcessedException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(ConversationBusyException.class)
    public ResponseEntity<ErrorResponse> handleConversationBusy(ConversationBusyException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), e.getMessage()));
    }

    @ExceptionHandler(InvalidActuatorCommandException.class)
    public ResponseEntity<ErrorResponse> handleInvalidActuatorCommand(InvalidActuatorCommandException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message));
    }
}