package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the words of the loop report, which the Discord section must not change. */
class ItemLoopWordingTest {
  private static final List<Detail> RATES =
      List.of(
          new Detail(
              "BEFORE",
              0,
              null,
              Map.of("killsPerMinute", 3.0, "goldPerMinute", 200.0, "campsPerMinute", 0.5)),
          new Detail(
              "AFTER",
              0,
              null,
              Map.of("killsPerMinute", 5.0, "goldPerMinute", 350.0, "campsPerMinute", 1.0)));

  private static String result(ItemEfficiency row) {
    return Wording.of(row.kind()).result(row, key -> key);
  }

  @Test
  void aMagicStickRow() {
    ItemEfficiency stick =
        new ItemEfficiency(
            0,
            "magic_stick",
            "magic_stick",
            "charge-restore",
            200.0,
            3,
            90,
            200L,
            1.5,
            null,
            Map.of("hp", 600.0, "mana", 300.0, "saves", 2.0, "diedSoon", 1.0),
            List.of("HP_UNCAPPED"),
            List.of());

    assertThat(result(stick))
        .isEqualTo(
            "3 uses, restored 600 HP and 300 mana, 2 saves, died within 10 s after 1 of them,"
                + " worth 1.5x its cost (his max health could not be estimated for some uses, so"
                + " every charge counts as health restored)");
  }

  @Test
  void anUnusedStick() {
    ItemEfficiency unused =
        new ItemEfficiency(
            0,
            "magic_stick",
            "magic_stick",
            "charge-restore",
            200.0,
            0,
            0,
            200L,
            null,
            null,
            Map.of(),
            List.of(),
            List.of());

    assertThat(result(unused)).isEqualTo("bought, never used");
  }

  @Test
  void aHandOfMidasRow() {
    ItemEfficiency midas =
        new ItemEfficiency(
            1,
            "hand_of_midas",
            "hand_of_midas",
            "midas-uses",
            600.0,
            4,
            500,
            2200L,
            0.9,
            1500.0,
            Map.of(
                "gold",
                800.0,
                "xp",
                400.0,
                "idleSeconds",
                30.0,
                "farmingSeconds",
                600.0,
                "heroGold",
                8000.0,
                "bonusAttackSpeed",
                35.0),
            List.of("COOLDOWN_ESTIMATED"),
            List.of());

    assertThat(result(midas))
        .isEqualTo(
            "4 uses, gave 800 gold (10% of his gold) and 400 XP, paid for itself at 25:00, ready"
                + " but unused 0:30 of the 10:00 he farmed with it, worth 0.9x its cost (the replay"
                + " shows no cooldown for it, so the ready time is the use plus the cooldown guess)"
                + " (it also gives +35 attack speed, not counted)");
  }

  @Test
  void theFarmingRowsWithAndWithoutDamageOrCost() {
    ItemEfficiency creeps =
        new ItemEfficiency(
            2,
            "farming",
            "battle_fury",
            "farm-rate",
            1500.0,
            20,
            900,
            4100L,
            1.8,
            1800.0,
            Map.of("netGoldPerMinute", 150.0),
            List.of(),
            RATES);
    ItemEfficiency damage =
        new ItemEfficiency(
            2,
            "farming",
            "maelstrom",
            "farm-rate",
            1500.0,
            20,
            900,
            2700L,
            0.6,
            null,
            Map.of(
                "netGoldPerMinute",
                -37.0,
                "damageValue",
                800.0,
                "creepGain",
                -900.0,
                "gain.farm-rate",
                -900.0,
                "gain.item-damage",
                800.0,
                "killsHelped",
                3.0),
            List.of("NO_DAMAGE_COMPARISON"),
            RATES);
    ItemEfficiency noCost =
        new ItemEfficiency(
            2,
            "farming",
            "maelstrom",
            "farm-rate",
            1500.0,
            20,
            900,
            null,
            null,
            null,
            Map.of(),
            List.of("COST_UNKNOWN"),
            RATES);
    String rates =
        "creeps 3.0 per min before and 5.0 after, creep gold 200 and 350 per min, camps 0.5 and"
            + " 1.0 per min";

    assertThat(result(creeps))
        .isEqualTo(rates + ", net 150 gold per min, paid back at 30:00, worth 1.8x its cost");
    assertThat(result(damage))
        .isEqualTo(
            rates
                + ", net -37 gold per min, farm -0.3x, fights +0.3x (3 kills helped), 0.6x in"
                + " total, not paid back by the end (bought too early or too late to compare his"
                + " damage before and after, so only the item's own damage counts)");
    assertThat(result(noCost))
        .isEqualTo(rates + " (the item constants give no cost for it, so there is no ratio)");
  }
}
