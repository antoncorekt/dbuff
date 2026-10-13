package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;

/** The heroes of both teams in one lane, with the window of the lane as a whole. */
record LanePair(Zone.Lane lane, LaningWindow window, List<LaneHero> heroes) {

  int id() {
    return lane.ordinal() + 1;
  }

  List<LaneHero> side(TeamSide team) {
    return heroes.stream().filter(h -> h.team() == team).toList();
  }

  static TeamSide other(TeamSide team) {
    return team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
  }
}
