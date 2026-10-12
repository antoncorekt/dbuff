package com.ako.dbuff.service.replay;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * What one parse of a replay produced. Extractors read narrow views of it and never touch the
 * parser library.
 *
 * @param draft the Ability Draft, null for a mode that has none or a replay with no pool
 * @param samples per-player state over time, null when the replay was not read past the draft
 * @param combatLog the hero-relevant combat-log entries, null when none were read
 * @param map the buildings and jungle camps of the map the game was played on
 * @param stateChanges items gained and lost, cooldowns and skill levels of the heroes
 * @param cutShort true when the replay ended with an error after the game had started, so the facts
 *     cover only the part that was read
 */
@Builder
public record ReplayFacts(
    int clockStartTick,
    double gameStartTime,
    List<ReplayPlayerFact> players,
    AbilityDraftFacts draft,
    SampleFacts samples,
    CombatLogFacts combatLog,
    MapFacts map,
    StateFacts stateChanges,
    boolean cutShort) {
  /** The players by their replay id; a player listed twice keeps the first entry. */
  public Map<Integer, ReplayPlayerFact> playersById() {
    return players.stream()
        .collect(
            Collectors.toMap(
                ReplayPlayerFact::replayPlayerId, Function.identity(), (first, second) -> first));
  }
}
