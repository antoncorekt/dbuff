package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import java.util.Set;

/** Works out the roles of one team for the game modes it supports. */
public interface RoleResolver {
  boolean supports(GameModeProfile mode);

  /** The stages this resolver produces; the analyzer asks it for no other. */
  Set<RoleStage> stages();

  /** The extractors whose output the resolver reads on top of what the analyzer always reads. */
  default Set<String> requires() {
    return Set.of();
  }

  /**
   * @return one assignment per player with each role 1 to 5 used once; empty when the team cannot
   *     be resolved
   */
  List<RoleAssignment> resolve(RoleContext context);
}
