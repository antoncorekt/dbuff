package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;

/**
 * One request to a {@link RoleResolver}.
 *
 * @param team the five players of one team, ordered by slot
 */
public record RoleContext(RoleStage stage, List<RolePlayer> team) {}
