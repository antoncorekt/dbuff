package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.farming;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.paid;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class HandOfMidasEvaluatorTest {
  private static final String MIDAS = "item_hand_of_midas";
  private static final String HELLCALLER = "npc_dota_neutral_satyr_hellcaller";
  private static final String MELEE = "npc_dota_creep_goodguys_melee";

  private final CreepValues creepValues = new CreepValues("replay/creep-values.json");
  private final HandOfMidasEvaluator evaluator = new HandOfMidasEvaluator(creepValues);

  private ItemEfficiency evaluate(long cost, HeroInputs hero, Map<String, Double> parameters) {
    ItemEfficiencyEntry entry =
        ItemTestData.entry(
            "hand_of_midas", HandOfMidasEvaluator.KIND, List.of("hand_of_midas"), parameters);
    return evaluator
        .evaluate(
            new EntryContext(
                entry, ItemTestData.kits(Map.of("hand_of_midas", cost)), 400, List.of(hero)))
        .getFirst();
  }

  private static List<CombatEvent> twoUses() {
    return List.of(
        use(200, MIDAS, HELLCALLER),
        paid(CombatEventKind.GOLD, 200, 160),
        paid(CombatEventKind.XP, 200, 95),
        use(300, MIDAS, MELEE),
        paid(CombatEventKind.GOLD, 300, 46),
        paid(CombatEventKind.GOLD, 300, 160),
        paid(CombatEventKind.XP, 300, 57));
  }

  private static List<ReplayStateChangeDomain> owned() {
    return List.of(change(100, StateChangeKind.ITEM_ADDED, MIDAS));
  }

  @Test
  void theBonusGoldAndTheExperienceOfEachUseAreSummedAndThePaybackIsFound() {
    ItemEfficiency result =
        evaluate(
            300, hero(owned(), twoUses(), null, List.of(), List.of(farming(100, 400))), Map.of());

    assertThat(result.count()).isEqualTo(2);
    assertThat(result.figures()).containsEntry("gold", 320.0).containsEntry("xp", 152.0);
    assertThat(result.gain()).isEqualTo(472);
    assertThat(result.ratio()).isEqualTo(472.0 / 300);
    assertThat(result.paybackSeconds()).isEqualTo(300.0);
    assertThat(result.rows())
        .extracting(ItemEfficiency.Detail::label)
        .containsExactly("USE", "USE", "PAYBACK");
    assertThat(result.rows().get(1).figures()).containsEntry("gold", 160.0);
    assertThat(result.rows().get(1).target()).isEqualTo(MELEE);
    assertThat(result.purchaseSeconds()).isEqualTo(100.0);
  }

  @Test
  void theGoldOfAFlagbearerOrAnotherKillAtTheSameSecondIsNotTakenForTheBonus() {
    ItemEfficiency result =
        evaluate(
            300, hero(owned(), twoUses(), null, List.of(), List.of(farming(100, 400))), Map.of());

    assertThat(result.rows().get(1).figures())
        .containsEntry("gold", 160.0)
        .containsEntry("xp", 57.0);
  }

  @Test
  void theTimeReadyButUnusedWhileFarmingIsCounted() {
    ItemEfficiency result =
        evaluate(
            5000, hero(owned(), twoUses(), null, List.of(), List.of(farming(100, 400))), Map.of());

    assertThat(result.rows().get(0).figures()).containsEntry("idleBefore", 100.0);
    assertThat(result.rows().get(1).figures()).containsEntry("idleBefore", 10.0);
    assertThat(result.figures()).containsEntry("idleSeconds", 120.0);
    assertThat(result.paybackSeconds()).as("never paid back").isNull();
    assertThat(result.flags()).contains(HandOfMidasEvaluator.COOLDOWN_ESTIMATED);
  }

  @Test
  void aCooldownTheReplayShowedIsUsedInsteadOfTheParameter() {
    var changes = new java.util.ArrayList<>(owned());
    ReplayStateChangeDomain cooldown = change(201, StateChangeKind.COOLDOWN, MIDAS);
    cooldown.setReadyAtSeconds(250.0);
    changes.add(cooldown);

    ItemEfficiency result =
        evaluate(
            5000,
            hero(changes, twoUses(), null, List.of(), List.of(farming(100, 400))),
            Map.of("cooldownSeconds", 1000.0));

    assertThat(result.rows().get(1).figures()).containsEntry("idleBefore", 50.0);
  }

  @Test
  void whatANormalKillWouldHavePaidIsEstimatedFromTheCreepValues() {
    ItemEfficiency result =
        evaluate(300, hero(owned(), twoUses(), null, List.of(), List.of()), Map.of());

    double melee = creepValues.of(CreepClass.LANE_MELEE).orElseThrow().gold();
    assertThat(result.rows().get(1).figures()).containsEntry("extraGold", 160 - melee);
    assertThat(result.flags()).contains(HandOfMidasEvaluator.EXTRA_ESTIMATED);
  }

  @Test
  void theExperienceWeightScalesTheGain() {
    ItemEfficiency result =
        evaluate(
            5000, hero(owned(), twoUses(), null, List.of(), List.of()), Map.of("xpWeight", 0.0));

    assertThat(result.gain()).isEqualTo(320);
  }

  @Test
  void theTotalsOfTheHeroAreKeptForComparison() {
    List<CombatEvent> events = new java.util.ArrayList<>(twoUses());
    events.add(paid(CombatEventKind.GOLD, 350, 1000));
    events.add(paid(CombatEventKind.GOLD, 360, -300));

    ItemEfficiency result =
        evaluate(300, hero(owned(), events, null, List.of(), List.of()), Map.of());

    assertThat(result.figures()).containsEntry("heroGold", 1366.0);
  }

  @Test
  void withoutABonusInTheConstantsNoGoldIsClaimedAndTheRowSaysSo() {
    List<CombatEvent> events =
        List.of(
            use(200, MIDAS, HELLCALLER),
            paid(CombatEventKind.GOLD, 200, -160),
            paid(CombatEventKind.GOLD, 200, 160),
            paid(CombatEventKind.XP, 200, 95));
    ItemEfficiencyEntry entry =
        ItemTestData.entry(
            "hand_of_midas", HandOfMidasEvaluator.KIND, List.of("hand_of_midas"), Map.of());
    ItemKits noBonus =
        key ->
            java.util.Optional.of(
                com.ako.dbuff.service.constant.data.ItemConstant.builder().cost(300L).build());

    ItemEfficiency result =
        evaluator
            .evaluate(
                new EntryContext(
                    entry,
                    noBonus,
                    400,
                    List.of(hero(owned(), events, null, List.of(), List.of()))))
            .getFirst();

    assertThat(result.figures()).containsEntry("gold", 0.0).containsEntry("xp", 95.0);
    assertThat(result.flags()).contains(HandOfMidasEvaluator.BONUS_GOLD_UNKNOWN);
  }

  @Test
  void aNegativeGoldEntryIsNeverTakenForTheBonus() {
    List<CombatEvent> events =
        List.of(
            use(200, MIDAS, HELLCALLER),
            paid(CombatEventKind.GOLD, 200, -300),
            paid(CombatEventKind.XP, 200, 95));

    ItemEfficiency result =
        evaluate(300, hero(owned(), events, null, List.of(), List.of()), Map.of());

    assertThat(result.figures()).containsEntry("gold", 0.0);
    assertThat(result.flags()).contains(HandOfMidasEvaluator.USE_WITHOUT_GOLD);
  }

  @Test
  void twoUsesWithinTheSameWindowEachTakeTheirOwnNearestExperienceOfACreepKill() {
    CombatEvent heroKillXp =
        new CombatEvent(
            CombatEventKind.XP, 200.0, "", ItemTestData.HERO, "", 0, 999, 0, 1, 0, 0, 0, 0);
    List<CombatEvent> events =
        List.of(
            use(200.0, MIDAS, HELLCALLER),
            use(200.1, MIDAS, HELLCALLER),
            heroKillXp,
            paid(CombatEventKind.GOLD, 200.0, 160),
            paid(CombatEventKind.XP, 200.0, 50),
            paid(CombatEventKind.GOLD, 200.1, 160),
            paid(CombatEventKind.XP, 200.1, 70));

    ItemEfficiency result =
        evaluate(5000, hero(owned(), events, null, List.of(), List.of()), Map.of());

    assertThat(result.rows())
        .extracting(d -> d.figures().get("xp"))
        .containsExactly(50.0, 70.0);
    assertThat(result.figures()).containsEntry("gold", 320.0).containsEntry("xp", 120.0);
  }

  @Test
  void aCostTheConstantsLackGivesAFlagAndNoRatioOrPayback() {
    ItemEfficiencyEntry entry =
        ItemTestData.entry(
            "hand_of_midas", HandOfMidasEvaluator.KIND, List.of("hand_of_midas"), Map.of());

    ItemEfficiency result =
        evaluator
            .evaluate(
                new EntryContext(
                    entry,
                    ItemTestData.kits(Map.of()),
                    400,
                    List.of(hero(owned(), twoUses(), null, List.of(), List.of()))))
            .getFirst();

    assertThat(result.cost()).isNull();
    assertThat(result.ratio()).isNull();
    assertThat(result.paybackSeconds()).isNull();
    assertThat(result.flags()).contains(HandOfMidasEvaluator.COST_UNKNOWN);
  }
}
