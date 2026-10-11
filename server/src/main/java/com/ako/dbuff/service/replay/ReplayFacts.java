package com.ako.dbuff.service.replay;

import java.util.List;

/**
 * What one parse of a replay produced. Extractors read narrow views of it and never touch the
 * parser library.
 *
 * @param draft the Ability Draft, null for a mode that has none
 */
public record ReplayFacts(
    int clockStartTick, double gameStartTime, List<ReplayPlayerFact> players, DraftFacts draft) {

  public ReplayFacts(int clockStartTick, double gameStartTime, List<ReplayPlayerFact> players) {
    this(clockStartTick, gameStartTime, players, null);
  }
}
