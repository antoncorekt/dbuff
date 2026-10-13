package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.kill;
import static org.assertj.core.api.Assertions.assertThat;

class FarmingGroupEvaluatorTest {
  private static final String CAMP_CREEP = "npc_dota_neutral_kobold";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "farming_items",
          FarmingGroupEvaluator.KIND,
          List.of("bfury", "maelstrom", "mjollnir"),
          Map.of());
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("bfury", 500L, "maelstrom", 500L, "mjollnir", 5500L));

  private final FarmingGroupEvaluator evaluator = new FarmingGroupEvaluator();

  private ItemEfficiency evaluate(HeroInputs hero, int gameEnd) {
    return evaluator.evaluate(new EntryContext(ENTRY, KITS, gameEnd, List.of(hero))).getFirst();
  }

  private static List<CreepKillDomain> kills(int count, double from, double to, int gold) {
    List<CreepKillDomain> kills = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      kills.add(kill(from + (to - from) * i / count, "npc_dota_creep_goodguys_melee", gold, null));
    }
    return kills;
  }

  @Test
  void theRatesBeforeAndAfterTheFirstItemGiveTheNetGainAndThePayback() {
    List<CreepKillDomain> kills = new ArrayList<>();
    kills.addAll(kills(10, 300, 600, 30));
    kills.addAll(kills(20, 600, 900, 30));
    kills.add(kill(610, CAMP_CREEP, 10, 3));
    kills.add(kill(615, CAMP_CREEP, 10, 3));
    kills.add(kill(700, CAMP_CREEP, 10, 3));
    var changes =
        List.of(
            change(600, StateChangeKind.ITEM_ADDED, "item_maelstrom"),
            change(1200, StateChangeKind.ITEM_ADDED, "item_mjollnir"));

    ItemEfficiency result = evaluate(hero(changes, List.of(), null, kills, List.of()), 1500);

    assertThat(result.itemKey()).isEqualTo("maelstrom");
    assertThat(result.purchaseSeconds()).isEqualTo(600.0);
    assertThat(result.cost()).isEqualTo(500);
    assertThat(result.count()).isEqualTo(23);
    assertThat(result.rows())
        .extracting(ItemEfficiency.Detail::label)
        .containsExactly("BEFORE", "AFTER", "PAYBACK");
    assertThat(result.rows().get(0).figures())
        .containsEntry("killsPerMinute", 2.0)
        .containsEntry("goldPerMinute", 60.0)
        .containsEntry("campsPerMinute", 0.0);
    assertThat(result.rows().get(1).figures())
        .containsEntry("goldPerMinute", (600 + 30) / 5.0)
        .containsEntry("campsPerMinute", 2 / 5.0);
    assertThat(result.figures()).containsEntry("netGoldPerMinute", 66.0);
    assertThat(result.gain()).isEqualTo(66.0 * 15);
    assertThat(result.paybackSeconds()).isEqualTo(600 + 500 / 66.0 * 60);
    assertThat(result.ratio()).isEqualTo(66.0 * 15 / 500);
  }

  @Test
  void anItemBoughtBeforeTwoMinutesOfFarmingCannotBeComparedAndSaysSo() {
    var changes = List.of(change(60, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, kills(5, 60, 300, 30), List.of()), 900);

    assertThat(result.flags()).containsExactly(FarmingGroupEvaluator.NO_BEFORE_WINDOW);
    assertThat(result.ratio()).isNull();
    assertThat(result.gain()).isZero();
    assertThat(result.rows()).hasSize(2);
  }

  @Test
  void anItemBoughtAtTheEndHasNoAfterWindow() {
    var changes = List.of(change(890, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, kills(10, 300, 890, 30), List.of()), 900);

    assertThat(result.flags()).contains(FarmingGroupEvaluator.SHORT_AFTER_WINDOW);
    assertThat(result.ratio()).isNull();
  }

  @Test
  void deniesAreNotCountedAndAHeroWithoutAGroupItemGetsNoRow() {
    assertThat(
            evaluator.evaluate(
                new EntryContext(
                    ENTRY,
                    KITS,
                    900,
                    List.of(hero(List.of(), List.of(), null, List.of(), List.of())))))
        .isEmpty();
    CreepKillDomain denied = kill(700, "npc_dota_creep_goodguys_melee", 0, null);
    denied.setDenied(true);
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, List.of(denied), List.of()), 1500);

    assertThat(result.count()).isZero();
  }
}
