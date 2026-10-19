package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.PlayerSlots;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the worth model reads across heroes: whole-match combat entries and the per-minute counters
 * of every player.
 *
 * @param events damage to heroes by anyone, hero deaths, the hero-kill gold and experience, and the
 *     heals that items did to heroes, in log order
 * @param slots player slot by combat-log hero name
 * @param minutes the minute rows of each slot, in minute order
 * @param roster every hero's inputs; a supplier because the inputs hold this record
 */
public record MatchFacts(
    List<CombatEvent> events,
    Map<String, Long> slots,
    Map<Long, List<ReplayPlayerMinuteDomain>> minutes,
    Supplier<List<HeroInputs>> roster) {

  public static final MatchFacts EMPTY = new MatchFacts(List.of(), Map.of(), Map.of());

  public MatchFacts(
      List<CombatEvent> events,
      Map<String, Long> slots,
      Map<Long, List<ReplayPlayerMinuteDomain>> minutes) {
    this(events, slots, minutes, List::of);
  }

  /** The other heroes on the hero's team. */
  public List<HeroInputs> allies(HeroInputs hero) {
    return roster.get().stream()
        .filter(other -> other.slot() != hero.slot())
        .filter(other -> PlayerSlots.radiant(other.slot()) == PlayerSlots.radiant(hero.slot()))
        .toList();
  }

  /** Whether both heroes are known and play for different teams. */
  public boolean enemies(String hero, String other) {
    Long a = slots.get(hero);
    Long b = slots.get(other);
    return a != null && b != null && PlayerSlots.radiant(a) != PlayerSlots.radiant(b);
  }

  public List<ReplayPlayerMinuteDomain> minutesOf(long slot) {
    return minutes.getOrDefault(slot, List.of());
  }
}
