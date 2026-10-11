package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FightAbilityAssessorTest {

  private static final String STORM = "sven_storm_bolt";
  private static final String GOD = "sven_gods_strength";
  private static final String PASSIVE = "sven_great_cleave";

  private final FightAbilityAssessor assessor = new FightAbilityAssessor(5);

  @Test
  void assess_countsCastsAndDamageInTheFightAndLeavesPassivesOut() {
    HeroStateTimeline state = state();
    Map<String, List<CombatEvent>> events = new HashMap<>();
    events.put(
        STORM, List.of(cast(CombatEventKind.ABILITY, 102, STORM), hit(103, STORM, 150, true)));

    var uses = assessor.assess(100, 120, state, 400, events, List.of());

    assertThat(uses).extracting(FightAbilityAssessor.Use::name).containsExactly(STORM, GOD);
    var storm = uses.getFirst();
    assertThat(storm.casts()).isEqualTo(1);
    assertThat(storm.firstCast()).isEqualTo(102.0);
    assertThat(storm.damage()).isEqualTo(150);
    assertThat(storm.reason()).isNull();
    assertThat(storm.level()).isEqualTo(2);
    assertThat(storm.manaCost()).isEqualTo(110);
  }

  @Test
  void assess_explainsAnAbilityNotUsed() {
    HeroStateTimeline cooling = state();
    cooling.add(change(StateChangeKind.COOLDOWN, GOD, 95, 130));

    assertThat(reason(cooling, 400, List.of(), GOD)).isEqualTo(NonCastReason.ON_COOLDOWN);
    assertThat(reason(state(), 50, List.of(), STORM)).isEqualTo(NonCastReason.NO_MANA);
    assertThat(reason(state(), 400, List.of(103.0), STORM)).isEqualTo(NonCastReason.DEAD_EARLY);
    assertThat(reason(state(), 400, List.of(112.0), STORM)).isEqualTo(NonCastReason.READY_NOT_CAST);
    assertThat(reason(state(), null, List.of(), STORM)).isEqualTo(NonCastReason.READY_NOT_CAST);
  }

  @Test
  void assess_saysWhenTheLastCastBeforeTheFightWasOnCreeps() {
    Map<String, List<CombatEvent>> events = new HashMap<>();
    events.put(
        STORM, List.of(cast(CombatEventKind.ABILITY, 80, STORM), hit(81, STORM, 100, false)));

    var storm = assessor.assess(100, 120, state(), 400, events, List.of()).getFirst();

    assertThat(storm.casts()).isZero();
    assertThat(storm.lastCastBefore()).isEqualTo(80.0);
    assertThat(storm.lastCastOnCreeps()).isTrue();
  }

  @Test
  void assess_listsAnItemHeldAndUsedAtSomePoint() {
    HeroStateTimeline state = state();
    state.add(change(StateChangeKind.ITEM_ADDED, "item_black_king_bar", 0, 0));
    state.add(change(StateChangeKind.COOLDOWN, "item_black_king_bar", 50, 110));

    var uses = assessor.assess(100, 120, state, 400, Map.of(), List.of());

    var bkb = uses.stream().filter(FightAbilityAssessor.Use::item).findFirst().orElseThrow();
    assertThat(bkb.name()).isEqualTo("item_black_king_bar");
    assertThat(bkb.cooldownLeft()).isEqualTo(10);
    assertThat(bkb.reason()).isEqualTo(NonCastReason.ON_COOLDOWN);
  }

  @Test
  void assess_leavesOutConsumablesAndTeleports() {
    HeroStateTimeline state = state();
    state.add(change(StateChangeKind.ITEM_ADDED, "item_tpscroll", 0, 0));
    state.add(change(StateChangeKind.ITEM_ADDED, "item_tango", 0, 0));
    state.add(change(StateChangeKind.COOLDOWN, "item_tpscroll", 50, 60));

    var uses = assessor.assess(100, 120, state, 400, Map.of(), List.of());

    assertThat(uses).noneMatch(FightAbilityAssessor.Use::item);
  }

  private NonCastReason reason(
      HeroStateTimeline state, Integer mana, List<Double> deaths, String ability) {
    return assessor.assess(100, 120, state, mana, Map.of(), deaths).stream()
        .filter(u -> u.name().equals(ability))
        .findFirst()
        .orElseThrow()
        .reason();
  }

  private static HeroStateTimeline state() {
    HeroStateTimeline state = new HeroStateTimeline();
    state.add(new HeroStateTimeline.Change(10, StateChangeKind.ABILITY_LEVEL, STORM, 0, 1, 0));
    state.add(new HeroStateTimeline.Change(10, StateChangeKind.MANA_COST, STORM, 0, 0, 100));
    state.add(new HeroStateTimeline.Change(20, StateChangeKind.ABILITY_LEVEL, GOD, 0, 1, 0));
    state.add(new HeroStateTimeline.Change(20, StateChangeKind.MANA_COST, GOD, 0, 0, 25));
    state.add(new HeroStateTimeline.Change(30, StateChangeKind.ABILITY_LEVEL, PASSIVE, 0, 1, 0));
    state.add(new HeroStateTimeline.Change(60, StateChangeKind.ABILITY_LEVEL, STORM, 0, 2, 0));
    state.add(new HeroStateTimeline.Change(60, StateChangeKind.MANA_COST, STORM, 0, 0, 110));
    return state;
  }

  private static HeroStateTimeline.Change change(
      StateChangeKind kind, String name, double second, double readyAt) {
    return new HeroStateTimeline.Change(second, kind, name, readyAt);
  }

  private static CombatEvent cast(CombatEventKind kind, double second, String name) {
    return event(kind, second, name, CombatEvent.ATTACKER_HERO, 0);
  }

  private static CombatEvent hit(double second, String name, int damage, boolean onHero) {
    int flags = CombatEvent.ATTACKER_HERO | (onHero ? CombatEvent.TARGET_HERO : 0);
    return event(CombatEventKind.DAMAGE, second, name, flags, damage);
  }

  private static CombatEvent event(
      CombatEventKind kind, double second, String name, int flags, int value) {
    return new CombatEvent(
        kind, second, "npc_dota_hero_sven", "x", name, flags, value, 0, 0, 0, 0, 0, 0);
  }
}
