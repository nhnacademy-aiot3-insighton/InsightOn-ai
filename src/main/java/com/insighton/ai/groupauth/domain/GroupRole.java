package com.insighton.ai.groupauth.domain;

public enum GroupRole {
    SUPER_MANAGER,
    MANAGER,
    MEMBER;

    public boolean isAtLeast(GroupRole minimum) {
        return this.ordinal() <= minimum.ordinal();
    }
}