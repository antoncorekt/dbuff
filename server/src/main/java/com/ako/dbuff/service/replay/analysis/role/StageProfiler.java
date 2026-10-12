package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;

/** Works out the function profile of each player of a team for one {@link RoleStage}. */
public interface StageProfiler {
  RoleStage stage();

  /**
   * @param team the five players of one team, ordered by slot
   * @return one profile per player, in the order of the team
   */
  List<FunctionProfile> profile(List<RolePlayer> team);
}
