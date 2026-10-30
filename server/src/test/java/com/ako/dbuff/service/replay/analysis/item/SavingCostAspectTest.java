package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.report.SavingCostLine;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.dealt;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.death;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static org.assertj.core.api.Assertions.assertThat;

class SavingCostAspectTest {
  private static final String RADIANCE = "item_radiance";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "radiance",
          List.of(SavingCostAspect.NAME),
          List.of("radiance"),
          Map.of("savingThreshold", 0.4, "smallPurchase", 500.0));
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("radiance", 5000L, "blink", 2250L, "tango", 90L, "relic", 3800L));
  private static final MatchFacts MATCH =
      new MatchFacts(
          List.of(),
          Map.of(ItemTestData.HERO, 0L, "npc_dota_hero_lion", 128L),
          Map.of(
              0L,
              List.of(
                  ItemTestData.minute(1, 0, 0),
                  ItemTestData.minute(2, 1200, 0),
                  ItemTestData.minute(7, 5000, 0),
                  ItemTestData.minute(8, 6000, 400),
                  ItemTestData.minute(9, 7000, 400))));

  /** Gold is 500 until 300 s, then 2500 (more than 40 percent of 5000) up to the purchase. */
  private static ReplayPlayerTrackDomain track(int saveFrom) {
    ReplayPlayerTrackDomain track = ItemTestData.track(200);
    int[] gold = new int[200];
    Arrays.fill(gold, 500);
    for (int i = sampleOf(saveFrom); i < 200; i++) {
      gold[i] = 2500 + (i - sampleOf(saveFrom)) * 10;
    }
    track.setGold(gold);
    int[] flags = new int[200];
    Arrays.fill(flags, 1);
    track.setFlags(flags);
    return track;
  }

  private static ItemEfficiency evaluate(
      List<com.ako.dbuff.dao.model.ReplayStateChangeDomain> changes,
      List<CombatEvent> events,
      ReplayPlayerTrackDomain track) {
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(changes, events, track, List.of(), List.of()), MATCH);
    List<ItemEfficiency> rows =
        ItemTestData.judge(ENTRY, List.of(new SavingCostAspect()), KITS, 900, List.of(hero));
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private static List<com.ako.dbuff.dao.model.ReplayStateChangeDomain> bought(double second) {
    return List.of(change(second, StateChangeKind.ITEM_ADDED, RADIANCE));
  }

  @Test
  void aCleanSaveHasTheWindowThePeakAndNoCost() {
    ItemEfficiency row = evaluate(bought(600), List.of(), track(300));

    assertThat(row.gain()).isZero();
    assertThat(row.flags()).isEmpty();
    ItemEfficiency.Detail saving = row.rows().getFirst();
    assertThat(saving.label()).isEqualTo("SAVING");
    assertThat(saving.seconds()).isEqualTo(300.0);
    assertThat(saving.figures())
        .containsEntry("savingSeconds", 300.0)
        .containsEntry("savingDeaths", 0.0)
        .containsEntry("savingGoldLost", 0.0);
    assertThat(saving.figures().get("savingPeakGold")).isGreaterThan(2500);
    assertThat(SavingCostLine.of(row))
        .hasValueSatisfying(l -> assertThat(l).startsWith("saved 3.1k over 5 min 0 s"));
  }

  @Test
  void aDeathWhileSavingCostsTheDeathAndIsCounted() {
    ItemEfficiency row = evaluate(bought(600), List.of(death(450)), track(300));

    assertThat(row.gain()).isNegative();
    assertThat(row.figures())
        .containsEntry("savingDeaths", 1.0)
        .containsEntry("savingGoldLost", 400.0);
    assertThat(row.figures().get("savingGoldHeldAtDeath")).isGreaterThan(2500);
    assertThat(row.figures().get("gain.saving-cost")).isEqualTo(row.gain());
    assertThat(SavingCostLine.of(row))
        .hasValueSatisfying(
            l -> assertThat(l).contains("died once holding 2.8k").contains("(lost 400)"));
  }

  @Test
  void aDeathWithoutTheMinuteGoldOfTheMatchIsFlagged() {
    HeroInputs hero =
        ItemTestData.hero(bought(600), List.of(death(450)), track(300), List.of(), List.of());

    ItemEfficiency row =
        ItemTestData.judge(ENTRY, List.of(new SavingCostAspect()), KITS, 900, List.of(hero))
            .getFirst();

    assertThat(row.flags()).contains(SavingCostAspect.NO_MINUTE_GOLD);
    assertThat(row.figures()).containsEntry("savingDeaths", 1.0);
    assertThat(SavingCostLine.of(row))
        .hasValueSatisfying(l -> assertThat(l).doesNotContain("lost"));
  }

  @Test
  void aDeathAfterTheGoldWasSpentElsewhereIsNotCounted() {
    ReplayPlayerTrackDomain spent = track(300);
    for (int i = sampleOf(400); i < 200; i++) {
      spent.getGold()[i] = 100;
    }

    ItemEfficiency row = evaluate(bought(600), List.of(death(450)), spent);

    assertThat(row.figures()).containsEntry("savingDeaths", 0.0);
    assertThat(row.gain()).isZero();
  }

  @Test
  void aSampleAtTheSecondOfABigPurchaseDoesNotStartTheWindow() {
    var changes =
        List.of(
            change(300, StateChangeKind.ITEM_ADDED, "item_blink"),
            change(600, StateChangeKind.ITEM_ADDED, RADIANCE));

    ItemEfficiency row = evaluate(changes, List.of(), track(300));

    assertThat(row.rows().getFirst().seconds()).isEqualTo(305.0);
  }

  @Test
  void aDeathBeforeTheWindowDoesNotCount() {
    ItemEfficiency row = evaluate(bought(600), List.of(death(200)), track(300));

    assertThat(row.gain()).isZero();
    assertThat(row.figures()).containsEntry("savingDeaths", 0.0);
  }

  @Test
  void aBigPurchaseInterruptsTheSaveAndTheWindowStartsAfterIt() {
    var changes =
        List.of(
            change(400, StateChangeKind.ITEM_ADDED, "item_blink"),
            change(600, StateChangeKind.ITEM_ADDED, RADIANCE));

    ItemEfficiency row = evaluate(changes, List.of(death(350)), track(300));

    assertThat(row.rows().getFirst().seconds()).isEqualTo(405.0);
    assertThat(row.figures())
        .containsEntry("savingSeconds", 195.0)
        .containsEntry("savingDeaths", 0.0);
    assertThat(row.gain()).isZero();
  }

  @Test
  void anItemPickedUpAgainDoesNotInterruptTheSave() {
    var changes =
        List.of(
            change(380, StateChangeKind.ITEM_REMOVED, "item_blink"),
            change(390, StateChangeKind.ITEM_ADDED, "item_blink"),
            change(600, StateChangeKind.ITEM_ADDED, RADIANCE));

    ItemEfficiency row = evaluate(changes, List.of(), track(300));

    assertThat(row.rows().getFirst().seconds()).isEqualTo(300.0);
  }

  @Test
  void aSmallPurchaseDoesNotInterruptTheSave() {
    var changes =
        List.of(
            change(400, StateChangeKind.ITEM_ADDED, "item_tango"),
            change(600, StateChangeKind.ITEM_ADDED, RADIANCE));

    ItemEfficiency row = evaluate(changes, List.of(), track(300));

    assertThat(row.rows().getFirst().seconds()).isEqualTo(300.0);
  }

  @Test
  void aPartOfTheItemDoesNotInterruptTheSave() {
    ItemKits withParts =
        key ->
            "radiance".equals(key)
                ? java.util.Optional.of(
                    com.ako.dbuff.service.constant.data.ItemConstant.builder()
                        .cost(5000L)
                        .components(List.of("relic"))
                        .build())
                : KITS.item(key);
    var changes =
        List.of(
            change(400, StateChangeKind.ITEM_ADDED, "item_relic"),
            change(600, StateChangeKind.ITEM_ADDED, RADIANCE));
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(changes, List.of(), track(300), List.of(), List.of()), MATCH);

    ItemEfficiency row =
        ItemTestData.judge(ENTRY, List.of(new SavingCostAspect()), withParts, 900, List.of(hero))
            .getFirst();

    assertThat(row.rows().getFirst().seconds()).isEqualTo(300.0);
  }

  @Test
  void withoutSavingTheGoldThereIsNoWindow() {
    ReplayPlayerTrackDomain poor = track(300);
    Arrays.fill(poor.getGold(), 500);

    ItemEfficiency row = evaluate(bought(600), List.of(death(450)), poor);

    assertThat(row.rows()).isEmpty();
    assertThat(row.gain()).isZero();
    assertThat(SavingCostLine.of(row)).isEmpty();
  }

  @Test
  void withoutAGoldTrackThereIsNoPartAndTheFlag() {
    ReplayPlayerTrackDomain noGold = track(300);
    noGold.setGold(null);

    ItemEfficiency row = evaluate(bought(600), List.of(death(450)), noGold);

    assertThat(row.flags()).containsExactly(SavingCostAspect.NO_GOLD_TRACK);
    assertThat(row.gain()).isZero();
    assertThat(row.rows()).isEmpty();
  }

  @Test
  void damageAndParticipationPerMinuteAreComparedWithTheWindowBefore() {
    CombatEvent kill =
        new CombatEvent(
            CombatEventKind.DEATH,
            200,
            ItemTestData.HERO,
            "npc_dota_hero_lion",
            "",
            CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
            0,
            0,
            0,
            0,
            0,
            0,
            0);
    MatchFacts withKill = new MatchFacts(List.of(kill), MATCH.slots(), MATCH.minutes());
    List<CombatEvent> events = List.of(dealt(150, 600, ""), dealt(400, 1200, ""));
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(bought(600), events, track(300), List.of(), List.of()), withKill);

    ItemEfficiency row =
        ItemTestData.judge(ENTRY, List.of(new SavingCostAspect()), KITS, 900, List.of(hero))
            .getFirst();

    assertThat(row.rows().getFirst().figures())
        .containsEntry("damagePerMinute", 240.0)
        .containsEntry("damagePerMinuteBefore", 120.0)
        .containsEntry("participationPerMinute", 0.0)
        .containsEntry("participationPerMinuteBefore", 0.2);
  }
}
