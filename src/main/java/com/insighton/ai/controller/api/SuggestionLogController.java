package com.insighton.ai.controller.api;

import com.insighton.ai.controller.swagger.SuggestionLogApi;
import com.insighton.ai.domain.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.domain.suggestion.service.SuggestionLogService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionLogController implements SuggestionLogApi {

    private final SuggestionLogService suggestionLogService;

    @Override
    @GetMapping
    public ResponseEntity<Page<SuggestionLogResponse>> getSuggestionLogs(
            @RequestParam Long groupId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        List<SuggestionLogResponse> content = suggestionLogService.findSuggestionLogs(groupId, locationId, from, to,
                pageable);
        long total = suggestionLogService.countSuggestionLogs(groupId, locationId, from, to);

        return ResponseEntity.ok(new PageImpl<>(content, pageable, total));
    }

    @Override
    @GetMapping("/{suggestionLogId}")
    public ResponseEntity<SuggestionLogResponse> getSuggestionLog(
            @PathVariable Long suggestionLogId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.findSuggestionLog(suggestionLogId, userId));
    }

    @Override
    @PostMapping("/{suggestionLogId}/accept")
    public ResponseEntity<SuggestionLogResponse> accept(
            @PathVariable Long suggestionLogId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.accept(suggestionLogId, userId));
    }

    @Override
    @PostMapping("/{suggestionLogId}/reject")
    public ResponseEntity<SuggestionLogResponse> reject(
            @PathVariable Long suggestionLogId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.reject(suggestionLogId, userId));
    }
}
