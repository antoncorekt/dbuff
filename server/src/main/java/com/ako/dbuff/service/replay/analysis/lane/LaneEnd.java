package com.ako.dbuff.service.replay.analysis.lane;

/** Why a hero's, or a lane's, laning stage ended. */
public enum LaneEnd {
  JUNGLE("went to farm the jungle"),
  OTHER_LANE("moved to another lane"),
  TELEPORTED("teleported away and did not come back"),
  LEFT("left the lane and did not come back"),
  TOWER_FELL("the lane tower fell"),
  OPPONENTS_LEFT("the other side had left the lane"),
  CAP("the time limit was reached"),
  GAME_END("the data of the hero ended");

  private final String words;

  LaneEnd(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
