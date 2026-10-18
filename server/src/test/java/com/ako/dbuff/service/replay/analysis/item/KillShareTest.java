package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KillShareTest {
  private static final String ME = ItemTestData.HERO;
  private static final String ALLY = "npc_dota_hero_crystal_maiden";
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final Map<String, Long> SLOTS = Map.of(ME, 0L, ALLY, 1L, ENEMY, 128L);
  private static final WorthPrices PRICES = new WorthPrices(10, 1, 0.02, 0.25, 8, 30);

  private static CombatEvent damage(String by, double second, int value) {
    return new CombatEvent(
        CombatEventKind.DAMAGE, second, by, ENEMY, "", HEROES, value, 500, 0, 0, 0, 0, 0);
  }

  private static CombatEvent dealt(double second, int value, String inflictor) {
    return new CombatEvent(
        CombatEventKind.DAMAGE, second, ME, ENEMY, inflictor, HEROES, value, 500, 0, 0, 0, 0, 0);
  }

  private static CombatEvent died(double second) {
    return new CombatEvent(
        CombatEventKind.DEATH, second, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent paid(CombatEventKind kind, String to, double second, int value) {
    int reason = kind == CombatEventKind.XP ? 1 : 12;
    return new CombatEvent(kind, second, "", to, "", 0, value, 0, reason, 0, 0, 0, 0);
  }

  private static KillShare share(List<CombatEvent> mine, List<CombatEvent> all, MatchFacts rest) {
    MatchFacts match = new MatchFacts(all, SLOTS, rest.minutes());
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(List.of(), mine, null, List.of(), List.of()), match);
    return new KillShare(hero, PRICES);
  }

  private static final MatchFacts LOST_300 =
      new MatchFacts(
          List.of(),
          Map.of(),
          Map.of(128L, List.of(ItemTestData.minute(1, 0, 0), ItemTestData.minute(2, 0, 300))));

  @Test
  void aDeathIsWorthTheBountyAndExperienceOfTheKillersAndTheGoldTheVictimLost() {
    List<CombatEvent> all =
        List.of(
            died(100),
            paid(CombatEventKind.GOLD, ME, 100, 200),
            paid(CombatEventKind.GOLD, ALLY, 100, 100),
            paid(CombatEventKind.XP, ME, 100, 150),
            paid(CombatEventKind.GOLD, ME, 140, 999));

    KillShare killShare = share(List.of(), all, LOST_300);

    assertThat(killShare.deaths()).hasSize(1);
    assertThat(killShare.deaths().getFirst().value()).isEqualTo(200 + 100 + 150 + 300);
  }

  @Test
  void aShareOfTheWindowDamageGetsThatShareOfTheDeath() {
    CombatEvent mine = dealt(95, 300, "");
    CombatEvent allies = damage(ALLY, 97, 700);
    List<CombatEvent> all =
        List.of(
            damage(ALLY, 80, 5000),
            mine,
            allies,
            died(100),
            paid(CombatEventKind.GOLD, ME, 100, 500));

    KillShare killShare = share(List.of(mine), all, MatchFacts.EMPTY);

    assertThat(killShare.value(e -> true)).isEqualTo(500 * 300.0 / 1000);
  }

  @Test
  void theFilterKeepsOnlyTheDamageOfOneSource() {
    CombatEvent proc = dealt(95, 100, "item_maelstrom");
    CombatEvent hit = dealt(96, 300, "");
    List<CombatEvent> all = List.of(proc, hit, died(100), paid(CombatEventKind.GOLD, ME, 100, 400));

    KillShare killShare = share(List.of(proc, hit), all, MatchFacts.EMPTY);

    assertThat(killShare.value(e -> "item_maelstrom".equals(e.inflictor())))
        .isEqualTo(400 * 100.0 / 400);
  }

  @Test
  void damageToAnEnemyWhoLivedIsPricedPerPoint() {
    CombatEvent poke = dealt(50, 1000, "");
    CombatEvent late = dealt(95, 100, "");
    List<CombatEvent> all = List.of(poke, late, died(300));

    KillShare killShare = share(List.of(poke, late), all, MatchFacts.EMPTY);

    assertThat(killShare.value(e -> true)).isCloseTo(1100 * 0.02, within(1e-9));
  }

  @Test
  void anAllyDeathAndDamageToAllyAreNotKills() {
    CombatEvent allyDeath =
        new CombatEvent(CombatEventKind.DEATH, 100, ENEMY, ALLY, "", HEROES, 0, 0, 0, 0, 0, 0, 0);

    KillShare killShare = share(List.of(), List.of(allyDeath), MatchFacts.EMPTY);

    assertThat(killShare.deaths()).isEmpty();
  }

  @Test
  void aDisableJustBeforeTheDeathIsAFixedShareOfIt() {
    List<CombatEvent> all = List.of(died(100), paid(CombatEventKind.GOLD, ME, 100, 400));

    KillShare killShare = share(List.of(), all, MatchFacts.EMPTY);

    assertThat(killShare.disableValue(ENEMY, 95)).isEqualTo(100);
    assertThat(killShare.disableValue(ENEMY, 80)).isZero();
    assertThat(killShare.disableValue(ENEMY, 101)).isZero();
  }
}
