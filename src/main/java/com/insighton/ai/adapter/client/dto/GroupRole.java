package com.insighton.ai.adapter.client.dto;

public enum GroupRole {
    SUPER_MANAGER,
    MANAGER,
    MEMBER;

    public boolean isAtLeast(GroupRole minimum) {
        return this.ordinal() <= minimum.ordinal();
    }
}