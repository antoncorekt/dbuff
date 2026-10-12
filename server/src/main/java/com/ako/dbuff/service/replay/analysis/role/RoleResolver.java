package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/** Works out the roles of one team for the game modes it supports. */
public interface RoleResolver {
  boolean supports(GameModeProfile mode);

  /**
   * @param team the five players of one team, ordered by slot
   * @return one assignment per player with each role 1 to 5 used once; empty when the team cannot
   *     be resolved
   */
  List<RoleAssignment> resolve(List<RolePlayer> team);
}
