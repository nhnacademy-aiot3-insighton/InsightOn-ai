package com.insighton.ai.adapter.client;

import com.insighton.ai.adapter.client.CoreClient;
import com.insighton.ai.adapter.client.dto.GroupRole;
import com.insighton.ai.adapter.client.dto.GroupMemberResponse;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import feign.FeignException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Core group_members 기준 그룹 소속·역할 검증 담당 서비스.
 */
@Service
public class GroupAuthorizationService {

    private final CoreClient coreClient;

    public GroupAuthorizationService(@Lazy CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    /**
     * userId가 groupId 멤버인지 확인.
     *
     * @param groupId 그룹 ID
     * @param userId  유저 ID
     * @return 해당 유저의 그룹 내 역할
     * @throws ForbiddenException 그룹 멤버가 아니거나, Core 응답의 groupId가 요청과 다른 경우
     */
    public GroupRole requireMembership(Long groupId, Long userId) {
        try {
            GroupMemberResponse member = coreClient.getGroupMemberByUserId(groupId, userId);

            if (!groupId.equals(member.groupId())) {
                throw new ForbiddenException(
                        "해당 그룹의 유저가 아닙니다.");
            }

            return member.groupRole();
        } catch (FeignException.NotFound e) {
            throw new ForbiddenException("groupId:" + groupId + "의 멤버가 아닙니다. userId:" + userId);
        }
    }

    /**
     * userId가 groupId 멤버이면서 최소 역할 이상인지 확인.
     *
     * @param groupId     그룹 ID
     * @param userId      유저 ID
     * @param minimumRole 요구되는 최소 역할
     * @throws ForbiddenException 멤버가 아니거나 역할이 부족한 경우
     */
    public void requireRole(Long groupId, Long userId, GroupRole minimumRole) {
        GroupRole role = requireMembership(groupId, userId);
        if (!role.isAtLeast(minimumRole)) {
            throw new ForbiddenException(
                    minimumRole + " 이상 권한이 필요합니다. userId:" + userId);
        }
    }
}
