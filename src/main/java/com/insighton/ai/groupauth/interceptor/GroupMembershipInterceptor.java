package com.insighton.ai.groupauth.interceptor;

import com.insighton.ai.exception.InvalidRequestException;
import com.insighton.ai.groupauth.service.GroupAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * groupId 쿼리 파라미터가 있는 요청에 한해 X-User-Id가 해당 그룹 멤버인지 검증. groupId가 없는 요청(상세조회·액션 등 ID 기반 엔드포인트)은 통과시키고, 그쪽은 Service 레이어에서
 * 별도 검증
 */
@Component
@RequiredArgsConstructor
public class GroupMembershipInterceptor implements HandlerInterceptor {

    private final GroupAuthorizationService groupAuthorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String groupIdParam = request.getParameter("groupId");
        if (groupIdParam == null) {
            return true;
        }

        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null) {
            throw new InvalidRequestException("X-User-Id 헤더가 없습니다.");
        }

        Long groupId;
        Long userId;
        try {
            groupId = Long.valueOf(groupIdParam);
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("groupId 또는 X-User-Id 형식이 올바르지 않습니다.");
        }

        groupAuthorizationService.requireMembership(groupId, userId);
        return true;
    }
}
