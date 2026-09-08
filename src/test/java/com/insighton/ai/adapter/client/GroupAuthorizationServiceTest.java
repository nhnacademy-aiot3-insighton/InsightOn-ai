package com.insighton.ai.adapter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.adapter.client.dto.GroupMemberResponse;
import com.insighton.ai.adapter.client.dto.GroupRole;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAuthorizationServiceTest {

    @Mock
    private CoreClient coreClient;

    @InjectMocks
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void requireMembership_정상_멤버면_역할을_반환한다() {
        given(coreClient.getGroupMemberByUserId(5L, 100L))
                .willReturn(new GroupMemberResponse(5L, GroupRole.MEMBER));

        GroupRole role = groupAuthorizationService.requireMembership(5L, 100L);

        assertThat(role).isEqualTo(GroupRole.MEMBER);
    }

    @Test
    void requireMembership_Core_응답의_groupId가_요청과_다르면_ForbiddenException() {
        given(coreClient.getGroupMemberByUserId(5L, 100L))
                .willReturn(new GroupMemberResponse(999L, GroupRole.MEMBER));

        assertThatThrownBy(() -> groupAuthorizationService.requireMembership(5L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireMembership_Core가_404를_반환하면_ForbiddenException() {
        given(coreClient.getGroupMemberByUserId(5L, 100L)).willThrow(notFound());

        assertThatThrownBy(() -> groupAuthorizationService.requireMembership(5L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRole_최소_역할_이상이면_예외_없이_통과한다() {
        given(coreClient.getGroupMemberByUserId(5L, 100L))
                .willReturn(new GroupMemberResponse(5L, GroupRole.SUPER_MANAGER));

        groupAuthorizationService.requireRole(5L, 100L, GroupRole.MANAGER);
    }

    @Test
    void requireRole_최소_역할_미만이면_ForbiddenException() {
        given(coreClient.getGroupMemberByUserId(5L, 100L))
                .willReturn(new GroupMemberResponse(5L, GroupRole.MEMBER));

        assertThatThrownBy(() -> groupAuthorizationService.requireRole(5L, 100L, GroupRole.MANAGER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRole_멤버가_아니면_ForbiddenException() {
        given(coreClient.getGroupMemberByUserId(5L, 100L)).willThrow(notFound());

        assertThatThrownBy(() -> groupAuthorizationService.requireRole(5L, 100L, GroupRole.MANAGER))
                .isInstanceOf(ForbiddenException.class);
    }

    private FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/internal/v1/groups/5/members",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8);
        return new FeignException.NotFound("not found", request, null, Map.of());
    }
}
