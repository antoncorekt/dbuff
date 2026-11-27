package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.DamageType;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The physical, magical and pure share of the damage enemy heroes dealt to a team's heroes. */
public record DamageShares(double physical, double magical, double pure) {

  /**
   * The shares over {@code (toSecond - windowSeconds, toSecond]} of hero-to-hero damage received by
   * {@code receiving}; null when no damage of a known type was dealt. Damage of an unknown type is
   * left out; an event without a type is read as physical when it has no inflictor, else by the
   * ability's type.
   */
  public static DamageShares of(
      List<CombatEvent> events,
      Map<String, TeamSide> teamByHero,
      TeamSide receiving,
      double toSecond,
      double windowSeconds,
      Function<String, DamageType> abilityType) {
    double physical = 0;
    double magical = 0;
    double pure = 0;
    double from = toSecond - windowSeconds;
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.DAMAGE
          || e.gameSecond() <= from
          || e.gameSecond() > toSecond
          || !e.attackerIsRealHero()
          || !e.targetIsRealHero()
          || teamByHero.get(e.target()) != receiving
          || teamByHero.get(e.attacker()) != receiving.opponent()) {
        continue;
      }
      switch (typeOf(e, abilityType)) {
        case PHYSICAL -> physical += e.value();
        case MAGICAL -> magical += e.value();
        case PURE -> pure += e.value();
        case UNKNOWN -> {}
      }
    }
    double total = physical + magical + pure;
    return total <= 0 ? null : new DamageShares(physical / total, magical / total, pure / total);
  }

  private static DamageType typeOf(CombatEvent e, Function<String, DamageType> abilityType) {
    if (e.damageType() != DamageType.UNKNOWN) {
      return e.damageType();
    }
    if (e.inflictor() == null || e.inflictor().isEmpty()) {
      return DamageType.PHYSICAL;
    }
    return abilityType.apply(e.inflictor());
  }
}
