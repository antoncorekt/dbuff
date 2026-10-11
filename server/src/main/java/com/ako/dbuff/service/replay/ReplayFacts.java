package com.ako.dbuff.service.replay;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What one parse of a replay produced. Extractors read narrow views of it and never touch the
 * parser library.
 *
 * @param draft the Ability Draft, null for a mode that has none
 * @param samples per-player state over time, null when the replay was not read past the draft
 * @param combatLog the hero-relevant combat-log entries, null when none were read
 * @param map the buildings and jungle camps of the map the game was played on
 */
public record ReplayFacts(
    int clockStartTick,
    double gameStartTime,
    List<ReplayPlayerFact> players,
    DraftFacts draft,
    SampleFacts samples,
    CombatLogFacts combatLog,
    MapFacts map) {

  /** The players by their replay id; a player listed twice keeps the first entry. */
  public Map<Integer, ReplayPlayerFact> playersById() {
    return players.stream()
        .collect(
            Collectors.toMap(
                ReplayPlayerFact::replayPlayerId, Function.identity(), (first, second) -> first));
  }

  public ReplayFacts(
      int clockStartTick,
      double gameStartTime,
      List<ReplayPlayerFact> players,
      DraftFacts draft,
      SampleFacts samples) {
    this(clockStartTick, gameStartTime, players, draft, samples, null, null);
  }

  public ReplayFacts(
      int clockStartTick,
      double gameStartTime,
      List<ReplayPlayerFact> players,
      DraftFacts draft,
      SampleFacts samples,
      CombatLogFacts combatLog) {
    this(clockStartTick, gameStartTime, players, draft, samples, combatLog, null);
  }

  public ReplayFacts(int clockStartTick, double gameStartTime, List<ReplayPlayerFact> players) {
    this(clockStartTick, gameStartTime, players, null, null, null, null);
  }

  public ReplayFacts(
      int clockStartTick, double gameStartTime, List<ReplayPlayerFact> players, DraftFacts draft) {
    this(clockStartTick, gameStartTime, players, draft, null, null, null);
  }
}
