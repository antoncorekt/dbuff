package com.ako.dbuff.service.replay.analysis.role;

public record RoleAssignment(
    long slot, int role, PlayerRoleSource source, double confidence, String evidence) {}
