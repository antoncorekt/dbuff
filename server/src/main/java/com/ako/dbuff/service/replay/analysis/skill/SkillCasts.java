package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The casts of a catalogued skill by one hero, from the combat log's ability entries with the skill
 * as inflictor (so a drafted or stolen skill counts), and what his state changes say about the
 * skill's level and cooldown.
 */
public final class SkillCasts {

  /**
   * @param ability the inflictor name
   * @param second game second of the cast
   * @param target the combat log's target name; {@code dota_unknown} for a point cast
   * @param level the skill level then, at least 1
   */
  public record Cast(String ability, double second, String target, int level) {}

  private final List<ReplayStateChangeDomain> changes;
  private final List<Cast> casts;

  private SkillCasts(List<ReplayStateChangeDomain> changes, List<Cast> casts) {
    this.changes = changes;
    this.casts = casts;
  }

  /**
   * @param hero the hero's name in the combat log
   * @param events the match's combat log
   * @param changes the hero's state changes, in time order
   * @param abilities the inflictor names of the skill
   */
  public static SkillCasts of(
      String hero,
      List<CombatEvent> events,
      List<ReplayStateChangeDomain> changes,
      Collection<String> abilities) {
    SkillCasts levels = new SkillCasts(changes, List.of());
    List<Cast> found =
        events.stream()
            .filter(
                e ->
                    e.kind() == CombatEventKind.ABILITY
                        && hero.equals(e.attacker())
                        && abilities.contains(e.inflictor()))
            .sorted(Comparator.comparingDouble(CombatEvent::gameSecond))
            .map(
                e ->
                    new Cast(
                        e.inflictor(),
                        e.gameSecond(),
                        e.target(),
                        Math.max(1, levels.levelAt(e.inflictor(), e.gameSecond()))))
            .toList();
    return new SkillCasts(changes, found);
  }

  public List<Cast> casts() {
    return casts;
  }

  /** The level the state changes show at the second; 0 when the skill was not learned yet. */
  public int levelAt(String ability, double second) {
    return changes.stream()
        .filter(
            c ->
                StateChangeKind.ABILITY_LEVEL.name().equals(c.getKind())
                    && ability.equals(c.getName())
                    && c.getGameSeconds() <= second
                    && c.getAbilityLevel() != null)
        .mapToInt(ReplayStateChangeDomain::getAbilityLevel)
        .max()
        .orElse(0);
  }

  /** True when the hero ever had a level of the skill. */
  public boolean everLearned(Collection<String> abilities) {
    return changes.stream()
        .anyMatch(
            c ->
                StateChangeKind.ABILITY_LEVEL.name().equals(c.getKind())
                    && abilities.contains(c.getName()));
  }

  /** True when the last cooldown the state changes show at the second had not ended. */
  public boolean onCooldown(String ability, double second) {
    return changes.stream()
        .filter(
            c ->
                StateChangeKind.COOLDOWN.name().equals(c.getKind())
                    && ability.equals(c.getName())
                    && c.getGameSeconds() <= second)
        .max(Comparator.comparingDouble(ReplayStateChangeDomain::getGameSeconds))
        .map(ReplayStateChangeDomain::getReadyAtSeconds)
        .filter(Objects::nonNull)
        .map(ready -> ready > second)
        .orElse(false);
  }
}
