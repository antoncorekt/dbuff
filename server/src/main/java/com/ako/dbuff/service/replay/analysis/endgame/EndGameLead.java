package com.ako.dbuff.service.replay.analysis.endgame;

/**
 * The losing team's net worth lead over a game, from one sample per minute: its peak, whether it
 * was gone by the end, and how long it held a given share.
 *
 * @param peakMinute the minute of the largest lead in gold, or -1 when the loser never led
 * @param peakGold the loser's lead in gold at that minute
 * @param peakShare that lead as a share of both teams' net worth together
 * @param lost the loser did not lead at the last minute
 * @param heldSeconds the longest run of samples with a lead of at least the throw share; each
 *     sample stands for a minute, so a lone one counts as 60 seconds
 */
public record EndGameLead(
    int peakMinute, double peakGold, double peakShare, boolean lost, int heldSeconds) {
  private static final int STEP_SECONDS = 60;

  public boolean everLed() {
    return peakMinute >= 0;
  }

  /**
   * @param loserGold the loser's net worth less the winner's at each minute
   * @param loserShare the same as a share of both teams' net worth together
   */
  public static EndGameLead of(double[] loserGold, double[] loserShare, double throwShare) {
    int peak = -1;
    for (int i = 0; i < loserGold.length; i++) {
      if (loserGold[i] > 0 && (peak < 0 || loserGold[i] > loserGold[peak])) {
        peak = i;
      }
    }
    int longest = 0;
    int run = 0;
    for (double share : loserShare) {
      run = share >= throwShare ? run + 1 : 0;
      longest = Math.max(longest, run);
    }
    boolean lost = loserGold.length == 0 || loserGold[loserGold.length - 1] <= 0;
    return new EndGameLead(
        peak,
        peak < 0 ? 0 : loserGold[peak],
        peak < 0 ? 0 : loserShare[peak],
        lost,
        longest * STEP_SECONDS);
  }
}
