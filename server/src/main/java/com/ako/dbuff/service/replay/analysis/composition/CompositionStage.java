package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleStage;

/** The point of view a team's composition is judged from. */
public enum CompositionStage {
  /** Right after the draft, from what the heroes and skills say. */
  PICK(RoleStage.INTENT),
  /** After the match, from what the players did. */
  FINAL(RoleStage.FINAL);

  private final RoleStage profileStage;

  CompositionStage(RoleStage profileStage) {
    this.profileStage = profileStage;
  }

  /** The stage of the player profiles this one is computed from. */
  public RoleStage profileStage() {
    return profileStage;
  }
}
