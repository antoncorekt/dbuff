package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.GoldReason;
import com.ako.dbuff.service.replay.XpReason;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What the hero's damage to enemy heroes was worth. Damage in the {@code killWindowSeconds} before
 * an enemy's death earns its share of that death's value; damage to an enemy who lived earns a
 * fixed price per point. A death is worth the bounty gold and experience the killers got plus the
 * gold the victim lost that minute.
 */
final class KillShare {
  private static final double SAME_MOMENT_SECONDS = 0.5;

  /** An enemy death, with the damage of everyone in its window and its value in gold. */
  record Death(String victim, double second, double value, double windowDamage) {}

  private final HeroInputs hero;
  private final WorthPrices prices;
  private final List<Death> deaths;

  KillShare(HeroInputs hero, WorthPrices prices) {
    this.hero = hero;
    this.prices = prices;
    this.deaths = enemyDeaths();
  }

  List<Death> deaths() {
    return deaths;
  }

  /**
   * The gold value of the hero's damage that passes the filter: its share of each enemy death plus
   * the poke price for damage outside every window.
   */
  double value(Predicate<CombatEvent> damageFilter) {
    double value = 0;
    for (Death death : deaths) {
      if (death.windowDamage() > 0) {
        value += death.value() * mineIn(death, damageFilter) / death.windowDamage();
      }
    }
    for (CombatEvent e : hero.events()) {
      if (isMyDamageOnEnemy(e) && damageFilter.test(e) && !inAnyWindow(e)) {
        value += e.value() * prices.pokePrice();
      }
    }
    return value;
  }

  /**
   * A disable cast on the victim just before the victim died: the fixed share of that death, 0 when
   * the victim did not die within the window.
   */
  double disableValue(String victim, double castSecond) {
    return deaths.stream()
        .filter(d -> d.victim().equals(victim))
        .filter(
            d -> d.second() >= castSecond && d.second() - castSecond <= prices.killWindowSeconds())
        .mapToDouble(d -> d.value() * prices.disableShare())
        .findFirst()
        .orElse(0);
  }

  private double mineIn(Death death, Predicate<CombatEvent> filter) {
    return hero.events().stream()
        .filter(e -> isMyDamageOnEnemy(e) && e.target().equals(death.victim()))
        .filter(e -> inWindow(e, death) && filter.test(e))
        .mapToDouble(CombatEvent::value)
        .sum();
  }

  private boolean isMyDamageOnEnemy(CombatEvent e) {
    return e.kind() == CombatEventKind.DAMAGE
        && hero.hero().equals(e.attacker())
        && e.targetIsRealHero()
        && hero.match().enemies(hero.hero(), e.target());
  }

  private boolean inAnyWindow(CombatEvent e) {
    return deaths.stream().anyMatch(d -> d.victim().equals(e.target()) && inWindow(e, d));
  }

  private boolean inWindow(CombatEvent e, Death death) {
    return e.gameSecond() <= death.second()
        && death.second() - e.gameSecond() < prices.killWindowSeconds();
  }

  private List<Death> enemyDeaths() {
    List<CombatEvent> events = hero.match().events();
    List<Death> found = new ArrayList<>();
    for (CombatEvent e : events) {
      if (e.kind() == CombatEventKind.DEATH
          && e.targetIsRealHero()
          && hero.match().enemies(hero.hero(), e.target())) {
        found.add(
            new Death(e.target(), e.gameSecond(), valueOf(e, events), windowDamage(e, events)));
      }
    }
    return List.copyOf(found);
  }

  private double windowDamage(CombatEvent death, List<CombatEvent> events) {
    return events.stream()
        .filter(e -> e.kind() == CombatEventKind.DAMAGE && death.target().equals(e.target()))
        .filter(e -> !death.target().equals(e.attacker()))
        .filter(
            e ->
                e.gameSecond() <= death.gameSecond()
                    && death.gameSecond() - e.gameSecond() < prices.killWindowSeconds())
        .mapToDouble(CombatEvent::value)
        .sum();
  }

  private double valueOf(CombatEvent death, List<CombatEvent> events) {
    double gold = 0;
    double xp = 0;
    for (CombatEvent e : events) {
      if (Math.abs(e.gameSecond() - death.gameSecond()) > SAME_MOMENT_SECONDS
          || !hero.match().enemies(death.target(), e.target())) {
        continue;
      }
      if (e.kind() == CombatEventKind.GOLD && e.goldReason() == GoldReason.HERO_KILL) {
        gold += Math.max(0, e.value());
      } else if (e.kind() == CombatEventKind.XP && e.xpReason() == XpReason.HERO_KILL) {
        xp += Math.max(0, e.value());
      }
    }
    int together = deathsTogether(death, events);
    return (gold + xp * prices.xpWeight()) / together + goldLost(death, events);
  }

  /** The deaths of the victim's team at the same moment, which share the killers' entries. */
  private int deathsTogether(CombatEvent death, List<CombatEvent> events) {
    long count =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && e.targetIsRealHero())
            .filter(e -> Math.abs(e.gameSecond() - death.gameSecond()) <= SAME_MOMENT_SECONDS)
            .filter(
                e ->
                    e.target().equals(death.target())
                        || (hero.match().slots().containsKey(e.target())
                            && !hero.match().enemies(death.target(), e.target())))
            .count();
    return (int) Math.max(1, count);
  }

  private double goldLost(CombatEvent death, List<CombatEvent> events) {
    Long slot = hero.match().slots().get(death.target());
    if (slot == null) {
      return 0;
    }
    double[] lost = MinuteSeries.during(hero.match().minutesOf(slot), MinuteSeries::lostToDeath);
    long sameMinute =
        events.stream()
            .filter(e -> e.kind() == CombatEventKind.DEATH && death.target().equals(e.target()))
            .filter(
                e ->
                    MinuteSeries.minuteOf(e.gameSecond())
                        == MinuteSeries.minuteOf(death.gameSecond()))
            .count();
    return MinuteSeries.at(lost, death.gameSecond()) / Math.max(1, sameMinute);
  }
}
