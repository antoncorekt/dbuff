package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.hero;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.kill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FarmingAspectsTest {
  private static final String CAMP_CREEP = "npc_dota_neutral_kobold";
  private static final ItemEfficiencyEntry ENTRY =
      ItemTestData.entry(
          "farming_items",
          List.of(FarmRateAspect.NAME),
          List.of("bfury", "maelstrom", "mjollnir"),
          Map.of());
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("bfury", 500L, "maelstrom", 500L, "mjollnir", 700L));

  private final List<ItemAspect> aspects =
      List.of(new FarmRateAspect(), new ItemDamageAspect(), new StatUpliftAspect());

  private ItemEfficiency evaluate(HeroInputs hero, int gameEnd) {
    return ItemTestData.judge(ENTRY, aspects, KITS, gameEnd, List.of(hero)).getFirst();
  }

  private static List<CreepKillDomain> history() {
    return kills(16, 120, 600, 30);
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
    kills.addAll(history());
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
    assertThat(result.cost()).isEqualTo(700L);
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
    assertThat(result.paybackSeconds()).isEqualTo(600 + 700 / 66.0 * 60);
    assertThat(result.ratio()).isEqualTo(66.0 * 15 / 700);
  }

  @Test
  void anItemBoughtBeforeTwoMinutesOfFarmingCannotBeComparedAndSaysSo() {
    var changes = List.of(change(60, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, kills(5, 60, 300, 30), List.of()), 900);

    assertThat(result.flags()).containsExactly(FarmWindows.NO_BEFORE_WINDOW);
    assertThat(result.ratio()).isNull();
    assertThat(result.gain()).isZero();
    assertThat(result.rows()).hasSize(2);
  }

  @Test
  void anItemBoughtAtTheEndHasNoAfterWindow() {
    var changes = List.of(change(890, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, kills(10, 300, 890, 30), List.of()), 900);

    assertThat(result.flags()).contains(FarmWindows.SHORT_AFTER_WINDOW);
    assertThat(result.ratio()).isNull();
  }

  @Test
  void deniesAreNotCountedAndAHeroWithoutAGroupItemGetsNoRow() {
    assertThat(
            ItemTestData.judge(
                ENTRY,
                aspects,
                KITS,
                900,
                List.of(hero(List.of(), List.of(), null, List.of(), List.of()))))
        .isEmpty();
    CreepKillDomain denied = kill(700, "npc_dota_creep_goodguys_melee", 0, null);
    denied.setDenied(true);
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, List.of(denied), List.of()), 1500);

    assertThat(result.count()).isZero();
  }

  @Test
  void theGainStopsWhenTheHeroNoLongerOwnsTheItem() {
    List<CreepKillDomain> kills = new ArrayList<>();
    kills.addAll(history());
    kills.addAll(kills(20, 600, 900, 30));
    var changes =
        List.of(
            change(600, StateChangeKind.ITEM_ADDED, "item_bfury"),
            change(900, StateChangeKind.ITEM_REMOVED, "item_bfury"));

    ItemEfficiency result = evaluate(hero(changes, List.of(), null, kills, List.of()), 1500);

    assertThat(result.figures()).containsEntry("minutesOwned", 5.0);
    assertThat(result.gain()).isEqualTo(60.0 * 5);
  }

  @Test
  void anItemBoughtBeforeTheHornHasNoBeforeFiguresRatherThanHugeOnes() {
    var changes = List.of(change(-70, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result =
        evaluate(hero(changes, List.of(), null, kills(5, 0, 200, 30), List.of()), 900);

    ItemEfficiency.Detail before = result.rows().getFirst();
    assertThat(before.label()).isEqualTo("BEFORE");
    assertThat(before.figures().get("killsPerMinute")).isNull();
    assertThat(before.figures().get("goldPerMinute")).isNull();
    assertThat(result.flags()).contains(FarmWindows.NO_BEFORE_WINDOW);
    assertThat(result.ratio()).isNull();
  }

  @Test
  void aKillWithoutASecondIsLeftOut() {
    CreepKillDomain nameless = kill(0, "npc_dota_creep_goodguys_melee", 30, null);
    nameless.setSeconds(null);
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));
    List<CreepKillDomain> kills = new ArrayList<>(history());
    kills.add(nameless);

    ItemEfficiency result = evaluate(hero(changes, List.of(), null, kills, List.of()), 1500);

    assertThat(result.rows().getFirst().figures()).containsEntry("kills", 10.0);
  }

  @Test
  void aCostTheConstantsLackGivesAFlagAndNoRatio() {
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));
    List<CreepKillDomain> kills = history();

    ItemEfficiency result =
        ItemTestData.judge(
                ENTRY,
                aspects,
                ItemTestData.kits(Map.of()),
                1500,
                List.of(hero(changes, List.of(), null, kills, List.of())))
            .getFirst();

    assertThat(result.cost()).isNull();
    assertThat(result.ratio()).isNull();
    assertThat(result.paybackSeconds()).isNull();
    assertThat(result.flags()).contains(AspectSum.COST_UNKNOWN);
  }

  @Test
  void theGainIsOnlyWhatComesAboveTheTrendOfTheHerosOwnCreepGold() {
    List<CreepKillDomain> kills = new ArrayList<>();
    for (int m = 2; m < 10; m++) {
      kills.add(kill(m * 60 + 10, "npc_dota_creep_goodguys_melee", 10 * m, null));
      kills.add(kill(m * 60 + 20, "npc_dota_creep_goodguys_melee", 10 * m, null));
    }
    for (int m = 10; m < 15; m++) {
      for (int i = 1; i <= 3; i++) {
        kills.add(kill(m * 60 + 10 * i, "npc_dota_creep_goodguys_melee", 90, null));
      }
    }
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));

    ItemEfficiency result = evaluate(hero(changes, List.of(), null, kills, List.of()), 1500);

    assertThat(result.figures().get("netGoldPerMinute")).isCloseTo(30.0, within(1e-6));
    assertThat(result.figures().get("gain.farm-rate")).isCloseTo(30.0 * 15, within(1e-6));
    assertThat(result.rows().get(0).figures().get("goldPerMinute")).isCloseTo(140.0, within(1e-6));
  }

  @Test
  void anItemBoughtBeforeTheHornCountsItsOwnDamageButNoUplift() {
    var changes = List.of(change(-70, StateChangeKind.ITEM_ADDED, "item_maelstrom"));
    List<CombatEvent> events = List.of(ItemTestData.dealt(100, 500, "item_maelstrom"));

    ItemEfficiencyEntry all =
        ItemTestData.entry(
            "farming_items",
            List.of(FarmRateAspect.NAME, ItemDamageAspect.NAME, StatUpliftAspect.NAME),
            List.of("bfury", "maelstrom", "mjollnir"),
            Map.of());

    ItemEfficiency result =
        ItemTestData.judge(
                all, aspects, KITS, 900, List.of(hero(changes, events, null, List.of(), List.of())))
            .getFirst();

    assertThat(result.figures())
        .containsEntry("procDamage", 500.0)
        .containsEntry("passiveDamage", 0.0);
    assertThat(result.flags()).contains(StatUpliftAspect.NO_DAMAGE_COMPARISON);
    assertThat(result.ratio()).isNull();
  }
}
