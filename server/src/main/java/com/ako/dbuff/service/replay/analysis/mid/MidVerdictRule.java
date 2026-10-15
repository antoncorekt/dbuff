package com.ako.dbuff.service.replay.analysis.mid;

import lombok.RequiredArgsConstructor;

/** The ACTIVE or PASSIVE rule of {@link MidActivityProperties}; a hypothesis, not a finding. */
@RequiredArgsConstructor
class MidVerdictRule {
  private final MidActivityProperties properties;

  /**
   * @param killsAfterLeaving null when he never left mid
   * @param xpPerMinute null when the timeline does not cover the window
   * @param enemyXpPerMinute null without an enemy mid or a timeline
   */
  record Facts(
      MidNext next,
      Integer killsAfterLeaving,
      int fightsJoined,
      double teamMedianFights,
      Double xpPerMinute,
      Double enemyXpPerMinute) {}

  MidVerdict verdict(Facts f) {
    boolean rotatedAndKilled =
        f.next() == MidNext.ROTATED
            && f.killsAfterLeaving() != null
            && f.killsAfterLeaving() >= properties.getActiveMinKills();
    boolean joinedAndLed =
        f.fightsJoined() >= properties.getMinFightsJoined()
            && f.fightsJoined() >= f.teamMedianFights()
            && f.xpPerMinute() != null
            && f.enemyXpPerMinute() != null
            && f.xpPerMinute() > f.enemyXpPerMinute();
    return rotatedAndKilled || joinedAndLed ? MidVerdict.ACTIVE : MidVerdict.PASSIVE;
  }
}
