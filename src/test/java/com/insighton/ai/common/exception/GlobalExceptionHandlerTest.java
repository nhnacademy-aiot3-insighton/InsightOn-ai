package com.insighton.ai.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.insighton.ai.adapter.client.exception.ActuatorNotFoundException;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.adapter.client.exception.InvalidActuatorCommandException;
import com.insighton.ai.domain.chatbot.exception.ConversationBusyException;
import com.insighton.ai.domain.enginealert.exception.EngineAlertNotFoundException;
import com.insighton.ai.domain.notification.exception.DashboardNotificationNotFoundException;
import com.insighton.ai.domain.report.exception.ReportNotFoundException;
import com.insighton.ai.domain.suggestion.exception.SuggestionAlreadyProcessedException;
import com.insighton.ai.domain.suggestion.exception.SuggestionLogNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_ReportNotFoundException은_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new ReportNotFoundException(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("1");
    }

    @Test
    void handleNotFound_SuggestionLogNotFoundException도_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new SuggestionLogNotFoundException(2L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleNotFound_DashboardNotificationNotFoundException도_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new DashboardNotificationNotFoundException(3L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleNotFound_EngineAlertNotFoundException도_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new EngineAlertNotFoundException(4L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleNotFound_ActuatorNotFoundException도_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ActuatorNotFoundException(5L, "AIRCON"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleSuggestionAlreadyProcessed_409를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleSuggestionAlreadyProcessed(
                new SuggestionAlreadyProcessedException(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void handleConversationBusy_409를_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleConversationBusy(new ConversationBusyException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleForbidden_403을_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(new ForbiddenException("권한 없음"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("권한 없음");
    }

    @Test
    void handleInvalidActuatorCommand_400을_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidActuatorCommand(
                new InvalidActuatorCommandException("AIRCON", "SET_TEMPERATURE", "999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleInvalidRequest_400을_반환한다() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidRequest(
                new InvalidRequestException("groupId는 필수값입니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("groupId는 필수값입니다.");
    }

    @Test
    void handleConstraintViolation_각_위반사항을_콤마로_이어붙여_400을_반환한다() {
        ConstraintViolation<?> violation = mockViolation("groupId", "must not be null");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("groupId: must not be null");
    }

    private ConstraintViolation<?> mockViolation(String propertyPathValue, String message) {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        org.mockito.Mockito.when(path.toString()).thenReturn(propertyPathValue);
        org.mockito.Mockito.when(violation.getPropertyPath()).thenReturn(path);
        org.mockito.Mockito.when(violation.getMessage()).thenReturn(message);
        return violation;
    }
}
