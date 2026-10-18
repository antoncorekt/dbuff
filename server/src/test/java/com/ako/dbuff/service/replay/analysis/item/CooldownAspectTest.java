package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.farming;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static org.assertj.core.api.Assertions.assertThat;

class CooldownAspectTest {
  private static final String OCTARINE = "item_octarine_core";
  private static final String ME = ItemTestData.HERO;
  private static final String ENEMY = "npc_dota_hero_lion";
  private static final String CALL = "axe_berserkers_call";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final double SHOWN_COOLDOWN = 15;

  private static ItemKits kits(boolean withReduction) {
    return key ->
        Optional.of(
            ItemConstant.builder()
                .cost(4800L)
                .attrib(
                    withReduction
                        ? List.of(Map.of("key", "bonus_cooldown", "value", "25"))
                        : List.of(Map.of("key", "bonus_mana", "value", "450")))
                .build());
  }

  private static CombatEvent cast(double second) {
    return new CombatEvent(
        CombatEventKind.ABILITY,
        second,
        ME,
        "",
        CALL,
        CombatEvent.ATTACKER_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent damage(double second, int value) {
    return new CombatEvent(
        CombatEventKind.DAMAGE, second, ME, ENEMY, CALL, HEROES, value, 500, 0, 0, 0, 0, 0);
  }

  private static CombatEvent died(double second) {
    return new CombatEvent(
        CombatEventKind.DEATH, second, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent gold(double second, int value) {
    return new CombatEvent(CombatEventKind.GOLD, second, "", ME, "", 0, value, 0, 12, 0, 0, 0, 0);
  }

  private static ReplayStateChangeDomain cooldown(double second) {
    ReplayStateChangeDomain c = change(second, StateChangeKind.COOLDOWN, CALL);
    c.setReadyAtSeconds(second + SHOWN_COOLDOWN);
    c.setCooldownLength(SHOWN_COOLDOWN);
    return c;
  }

  private static ItemEfficiency evaluate(
      List<Double> castSeconds, List<CombatEvent> more, boolean withReduction) {
    List<CombatEvent> events = new ArrayList<>(more);
    List<ReplayStateChangeDomain> changes =
        new ArrayList<>(List.of(change(100, StateChangeKind.ITEM_ADDED, OCTARINE)));
    for (double t : castSeconds) {
      events.add(cast(t));
      changes.add(cooldown(t));
    }
    events.sort((a, b) -> Double.compare(a.gameSecond(), b.gameSecond()));
    MatchFacts match = new MatchFacts(events, Map.of(ME, 0L, ENEMY, 128L), Map.of());
    HeroInputs hero =
        ItemTestData.heroWith(
            hero(changes, events, null, List.of(), List.of(farming(100, 400))), match);
    ItemEfficiencyEntry entry =
        ItemTestData.entry(
            "octarine_core", List.of(CooldownAspect.NAME), List.of("octarine_core"), Map.of());
    return ItemTestData.judge(
            entry, List.of(new CooldownAspect()), kits(withReduction), 400, List.of(hero))
        .getFirst();
  }

  @Test
  void aCastSoonerThanTheBaseCooldownIsExtraAndOneAfterItIsNot() {
    ItemEfficiency result = evaluate(List.of(200.0, 212.0, 240.0), List.of(), true);

    assertThat(result.count()).isEqualTo(1);
    assertThat(result.figures()).containsEntry("casts", 3.0).containsEntry("extraCasts", 1.0);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst().seconds()).isEqualTo(212.0);
    assertThat(result.rows().getFirst().figures())
        .containsEntry("gap", 12.0)
        .containsEntry("baseCooldown", 20.0);
  }

  @Test
  void aGapBetweenTheShownAndTheBaseCooldownIsExtraAndOneJustAtTheBaseIsNot() {
    assertThat(evaluate(List.of(200.0, 215.0), List.of(), true).count()).isEqualTo(1);
    assertThat(evaluate(List.of(200.0, 220.0), List.of(), true).count()).isZero();
  }

  @Test
  void theDamageOfAnExtraCastIsValuedByTheKillItHelped() {
    List<CombatEvent> fight =
        List.of(damage(213, 300), died(215), gold(215, 500), damage(241, 300), died(244));

    ItemEfficiency result = evaluate(List.of(200.0, 212.0, 240.0), fight, true);

    assertThat(result.figures()).containsEntry("killsHelped", 1.0);
    assertThat(result.gain()).isEqualTo(500.0);
    assertThat(result.ratio()).isEqualTo(500.0 / 4800);
  }

  @Test
  void aCastBeforeThePurchaseIsOnlyThePreviousCastNotCounted() {
    ItemEfficiency result = evaluate(List.of(95.0, 105.0), List.of(), true);

    assertThat(result.figures()).containsEntry("casts", 1.0).containsEntry("extraCasts", 1.0);
  }

  @Test
  void withoutAReductionInTheConstantsNothingIsCountedAndTheRowSaysSo() {
    ItemEfficiency result = evaluate(List.of(200.0, 212.0), List.of(), false);

    assertThat(result.count()).isZero();
    assertThat(result.flags()).contains(CooldownAspect.REDUCTION_UNKNOWN);
  }
}
