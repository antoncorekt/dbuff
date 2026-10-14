package com.ako.dbuff.service.replay.analysis.endgame;

import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;

/**
 * The losing team's net worth lead over a game, from one sample per minute.
 *
 * @param peakMinute the minute of the largest lead in gold, or -1 when the loser never led
 *     meaningfully: the peak must reach both the minimum gold and the minimum share
 * @param peakGold the loser's lead in gold at that minute
 * @param peakShare that lead as a share of both teams' net worth together
 * @param lost the loser did not lead at the last whole minute sample, which may be before the game
 *     ended
 * @param lastGold the loser's lead in gold at that last sample; negative when it trailed
 * @param heldSeconds the longest run of samples that each lead by at least the throw share and
 *     throw gold, starting no earlier than the throw minute; each sample stands for a minute
 */
public record EndGameLead(
    int peakMinute,
    double peakGold,
    double peakShare,
    boolean lost,
    double lastGold,
    int heldSeconds) {

  /**
   * @param minLeadGold the peak must be at least this much gold to count as a lead
   * @param minLeadShare ...and at least this share of both teams' net worth together
   * @param throwShare a throwable lead is at least this share
   * @param throwGold ...and at least this much gold
   * @param throwMinute ...and is held in samples from this minute on
   */
  public record Rules(
      double minLeadGold,
      double minLeadShare,
      double throwShare,
      double throwGold,
      int throwMinute) {}

  public boolean everLed() {
    return peakMinute >= 0;
  }

  /**
   * @param loserGold the loser's net worth less the winner's at each minute
   * @param loserShare the same as a share of both teams' net worth together
   */
  public static EndGameLead of(double[] loserGold, double[] loserShare, Rules rules) {
    int peak = -1;
    for (int i = 0; i < loserGold.length; i++) {
      if (loserGold[i] > 0 && (peak < 0 || loserGold[i] > loserGold[peak])) {
        peak = i;
      }
    }
    if (peak >= 0
        && (loserGold[peak] < rules.minLeadGold() || loserShare[peak] < rules.minLeadShare())) {
      peak = -1;
    }
    int longest = 0;
    int run = 0;
    for (int i = 0; i < loserGold.length; i++) {
      boolean held =
          i >= rules.throwMinute()
              && loserShare[i] >= rules.throwShare()
              && loserGold[i] >= rules.throwGold();
      run = held ? run + 1 : 0;
      longest = Math.max(longest, run);
    }
    double last = loserGold.length == 0 ? 0 : loserGold[loserGold.length - 1];
    return new EndGameLead(
        peak,
        peak < 0 ? 0 : loserGold[peak],
        peak < 0 ? 0 : loserShare[peak],
        last <= 0,
        last,
        longest * TeamNetWorth.STEP_SECONDS);
  }
}
