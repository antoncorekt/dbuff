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
    kills.addAll(kills(10, 300, 600, 30));
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
    List<CreepKillDomain> kills = new ArrayList<>(kills(10, 300, 600, 30));
    kills.add(nameless);

    ItemEfficiency result = evaluate(hero(changes, List.of(), null, kills, List.of()), 1500);

    assertThat(result.rows().getFirst().figures()).containsEntry("kills", 10.0);
  }

  @Test
  void aCostTheConstantsLackGivesAFlagAndNoRatio() {
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_bfury"));
    List<CreepKillDomain> kills = kills(10, 300, 600, 30);

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

  private static ItemEfficiencyEntry withDamage() {
    return ItemTestData.entry(
        "farming_items",
        List.of(FarmRateAspect.NAME, ItemDamageAspect.NAME, StatUpliftAspect.NAME),
        List.of("bfury", "maelstrom", "mjollnir"),
        Map.of("damagePrice", 100.0, "damageWindowMinutes", 5.0));
  }

  private static List<CombatEvent> damageEvents() {
    return List.of(
        ItemTestData.dealt(350, 1000, ""),
        ItemTestData.dealt(450, 1000, ""),
        ItemTestData.dealt(550, 1000, ""),
        ItemTestData.dealt(650, 1500, ""),
        ItemTestData.dealt(700, 750, "item_maelstrom"),
        ItemTestData.dealt(750, 1500, ""),
        ItemTestData.dealt(800, 750, "item_maelstrom"),
        ItemTestData.dealt(850, 1500, ""));
  }

  @Test
  void theDamagePartAddsTheItemsOwnDamageAndTheUpliftOfItsStatsToTheGoldPart() {
    List<CreepKillDomain> kills = new ArrayList<>();
    kills.addAll(kills(10, 300, 600, 30));
    kills.addAll(kills(20, 600, 900, 30));
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_maelstrom"));

    ItemEfficiency result =
        ItemTestData.judge(
                withDamage(),
                aspects,
                KITS,
                1500,
                List.of(hero(changes, damageEvents(), null, kills, List.of())))
            .getFirst();

    assertThat(result.figures())
        .containsEntry("procDamage", 1500.0)
        .containsEntry("passiveDamage", 4500.0)
        .containsEntry("damageValue", 600.0)
        .containsEntry("creepGain", 60.0 * 15);
    assertThat(result.gain()).isEqualTo(60.0 * 15 + 600);
    assertThat(result.ratio()).isEqualTo((60.0 * 15 + 600) / 500);
    ItemEfficiency.Detail damage =
        result.rows().stream().filter(d -> d.label().equals("DAMAGE")).findFirst().orElseThrow();
    assertThat(damage.figures())
        .containsEntry("beforePerMinute", 600.0)
        .containsEntry("afterPerMinute", 1200.0)
        .containsEntry("damagePrice", 100.0)
        .containsEntry("bonusDamage", 25.0);
    assertThat(result.paybackSeconds()).isEqualTo(600 + 500 / ((60.0 * 15 + 600) / 15) * 60);
  }

  @Test
  void withoutTheParameterTheDamagePartIsOffAndTheResultIsTheGoldOnly() {
    List<CreepKillDomain> kills = new ArrayList<>();
    kills.addAll(kills(10, 300, 600, 30));
    kills.addAll(kills(20, 600, 900, 30));
    var changes = List.of(change(600, StateChangeKind.ITEM_ADDED, "item_maelstrom"));

    ItemEfficiency result = evaluate(hero(changes, damageEvents(), null, kills, List.of()), 1500);

    assertThat(result.figures()).doesNotContainKey("damageValue");
    assertThat(result.gain()).isEqualTo(60.0 * 15);
  }

  @Test
  void anItemBoughtBeforeTheHornCountsItsOwnDamageButNoUplift() {
    var changes = List.of(change(-70, StateChangeKind.ITEM_ADDED, "item_maelstrom"));

    ItemEfficiency result =
        ItemTestData.judge(
                withDamage(),
                aspects,
                KITS,
                900,
                List.of(hero(changes, damageEvents(), null, List.of(), List.of())))
            .getFirst();

    assertThat(result.figures())
        .containsEntry("procDamage", 1500.0)
        .containsEntry("passiveDamage", 0.0);
    assertThat(result.flags()).contains(StatUpliftAspect.NO_DAMAGE_COMPARISON);
    assertThat(result.ratio()).isNull();
  }
}
