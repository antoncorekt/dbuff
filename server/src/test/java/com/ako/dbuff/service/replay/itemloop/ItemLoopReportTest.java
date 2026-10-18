package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.itemloop.ItemLoopReport.MatchData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemLoopReportTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Windrunner", 128L, "Axe");
  private static final Map<String, String> NAMES =
      Map.of(
          "magic_stick", "Magic Stick",
          "hand_of_midas", "Hand of Midas",
          "maelstrom", "Maelstrom");

  private static Map<String, Double> figures(Object... pairs) {
    Map<String, Double> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
    }
    return map;
  }

  private static MatchData data(List<ItemEfficiency> rows) {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    return new MatchData(1, match, "Ability Draft", HEROES, rows, NAMES::get);
  }

  private static ItemEfficiency stick() {
    return new ItemEfficiency(
        0,
        "magic_stick",
        "magic_stick",
        "charge-restore",
        -60.0,
        2,
        620,
        200L,
        3.1,
        null,
        figures("charges", 20, "hp", 420, "mana", 210, "saves", 1, "diedSoon", 0),
        List.of("HP_UNCAPPED"),
        List.of(
            new Detail(
                "USE",
                346.8,
                null,
                figures(
                    "charges",
                    10,
                    "hp",
                    150,
                    "mana",
                    150,
                    "laning",
                    1,
                    "saved",
                    1,
                    "diedSoon",
                    0,
                    "hpBefore",
                    201,
                    "maxHp",
                    809,
                    "damageAround",
                    377)),
            new Detail(
                "USE",
                900,
                null,
                figures(
                    "charges",
                    10,
                    "hp",
                    270,
                    "mana",
                    60,
                    "laning",
                    0,
                    "saved",
                    0,
                    "diedSoon",
                    1,
                    "damageAround",
                    100))));
  }

  private static ItemEfficiency midas() {
    return new ItemEfficiency(
        128,
        "hand_of_midas",
        "hand_of_midas",
        "midas-uses",
        390.0,
        1,
        3781,
        2200L,
        1.7,
        1292.0,
        figures(
            "gold", 2720, "xp", 1061, "heroGold", 30207, "idleSeconds", 46, "farmingSeconds", 1110),
        List.of("COOLDOWN_ESTIMATED"),
        List.of(
            new Detail(
                "USE",
                387.3,
                "npc_dota_neutral_centaur_khan",
                figures(
                    "gold", 160, "xp", 90, "idleBefore", 12, "cumulative", 250, "extraGold", 100)),
            new Detail("PAYBACK", 1292.0, null, figures("cumulative", 2300))));
  }

  private static ItemEfficiency farming() {
    return new ItemEfficiency(
        0,
        "farming_items",
        "maelstrom",
        "farm-rate",
        600.0,
        23,
        990,
        2950L,
        0.3,
        null,
        figures("netGoldPerMinute", 66),
        List.of(),
        List.of(
            new Detail(
                "BEFORE",
                300,
                null,
                figures(
                    "minutes",
                    5,
                    "kills",
                    10,
                    "killsPerMinute",
                    2,
                    "goldPerMinute",
                    60,
                    "campsPerMinute",
                    0)),
            new Detail(
                "AFTER",
                600,
                null,
                figures(
                    "minutes",
                    5,
                    "kills",
                    23,
                    "killsPerMinute",
                    4.6,
                    "goldPerMinute",
                    126,
                    "campsPerMinute",
                    0.4))));
  }

  @Test
  void render_laysOutTheTwoTablesInWordsWithEmptyVerdictColumnsLast() {
    String report = ItemLoopReport.render(data(List.of(stick(), midas(), farming())));

    assertThat(report)
        .contains("**How to read this:**", "**Summary:**")
        .contains("3 hero rows (1 farming items, 1 hand of midas, 1 magic stick)")
        .contains("## Items", "## Item details")
        .contains(
            "| Windrunner: Magic Stick | -1:00 | 2 | 2 uses, restored 420 HP and 210 mana, 1 save,"
                + " worth 3.1x its cost (his max health could not be estimated for some uses, so"
                + " every charge counts as health restored) | 3.1x | | |")
        .contains("| Axe: Hand of Midas | 6:30 | 1 | 1 use, gave 2.7k gold (9% of his gold)")
        .contains("paid for itself at 21:32")
        .contains(
            "| Windrunner: Maelstrom | 10:00 | - | creeps 2.0 per min before and 4.6 after, creep"
                + " gold 60 and 126 per min, camps 0.0 and 0.4 per min, net 66 gold per min, not"
                + " paid back by the end, worth 0.3x its cost | 0.3x | | |")
        .contains(
            "| Windrunner: Magic Stick, use 1 at 5:47 | 5:47 (laning) | - | 150 HP, 150 mana (10"
                + " charges) | health 201 of 809, 377 damage within 3 s, a save, he lived | | |")
        .contains("| Windrunner: Magic Stick, use 2 at 15:00 | 15:00 | - |")
        .contains("health unknown, 100 damage within 3 s, he died within 10 s")
        .contains(
            "| Axe: Hand of Midas, use 1 at 6:27 | 6:27 | centaur khan | 160 gold, 90 XP | ready"
                + " and unused for 0:12 while he farmed, gain so far 250, 100 gold more than a"
                + " normal kill | | |")
        .contains("| Axe: Hand of Midas, paid for itself at 21:32 | 21:32 | - | paid for itself |")
        .contains(
            "| Windrunner: Maelstrom, before the purchase | 5:00 | - | 10 creeps (2.0 per min)")
        .doesNotContain("charge-restore", "farm-rate", "HP_UNCAPPED", "npc_dota", "USE", "PAYBACK");
  }

  @Test
  void render_namesAnItemTheConstantsDoNotKnowFromItsKey() {
    ItemEfficiency unknown =
        new ItemEfficiency(
            0,
            "x",
            "some_item",
            "new-kind",
            10.0,
            1,
            5,
            100L,
            0.05,
            null,
            Map.of(),
            List.of(),
            List.of());

    String report = ItemLoopReport.render(data(List.of(unknown)));

    assertThat(report)
        .contains("| Windrunner: some item | 0:10 | 1 | 1 use, gain 5, cost 100 | 0.1x |");
  }

  @Test
  void render_saysSoWhenNothingIsStored() {
    String report = ItemLoopReport.render(data(List.of()));

    assertThat(report).contains("no whitelisted item bought by any hero");
  }

  @Test
  void render_givesTheCreepPartTheDamagePartAndTheTotalOfAFarmingItem() {
    ItemEfficiency item =
        new ItemEfficiency(
            0,
            "farming_items",
            "mjollnir",
            "farm-rate",
            600.0,
            23,
            3800,
            5500L,
            0.7,
            null,
            figures("netGoldPerMinute", 66, "creepGain", 2200, "damageValue", 1650),
            List.of(),
            List.of(
                new Detail(
                    "BEFORE",
                    300,
                    null,
                    figures(
                        "minutes",
                        5,
                        "kills",
                        10,
                        "killsPerMinute",
                        2,
                        "goldPerMinute",
                        60,
                        "campsPerMinute",
                        0)),
                new Detail(
                    "AFTER",
                    600,
                    null,
                    figures(
                        "minutes",
                        5,
                        "kills",
                        23,
                        "killsPerMinute",
                        4.6,
                        "goldPerMinute",
                        126,
                        "campsPerMinute",
                        0.4)),
                new Detail(
                    "DAMAGE",
                    600,
                    null,
                    figures(
                        "procDamage",
                        6000,
                        "passiveDamage",
                        10500,
                        "damageValue",
                        1650,
                        "damagePrice",
                        100,
                        "beforePerMinute",
                        600,
                        "afterPerMinute",
                        1200,
                        "bonusDamage",
                        25,
                        "bonusAttackSpeed",
                        90))));

    String report = ItemLoopReport.render(data(List.of(item)));

    assertThat(report)
        .contains("creeps +0.4x its cost, damage +0.3x, 0.7x in total, not paid back")
        .contains("| Windrunner: mjollnir, damage at 10:00 | 10:00 | enemy heroes | 6k damage")
        .contains("worth 1.7k gold at 100 per 1k damage")
        .doesNotContain("DAMAGE");
  }
}
