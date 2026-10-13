package com.ako.dbuff.service.replay.analysis.behaviour;

/** Why a rotation turned out as it did; several can apply to one. */
public enum RotationReason {
  DIED("died on the trip"),
  DID_NOTHING("did nothing there"),
  INCOME_DROP("farm dropped"),
  ARRIVED_AFTER_FIGHT_ENDED("arrived after the fight was over"),
  LEFT_LANE_UNDEFENDED("left the lane undefended"),
  TOWER_LOST_WHILE_AWAY("an own tower fell while he was away");

  private final String words;

  RotationReason(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
