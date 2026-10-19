package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.FarmingKindWording;
import com.ako.dbuff.service.replay.report.ItemWording;
import com.ako.dbuff.service.replay.report.SavingCostLine;
import java.util.Map;
import java.util.function.Function;

/** The farming group: creeps, creep gold and camps per minute before and after the purchase. */
final class FarmingWording implements Wording {
  private static final FarmingKindWording KIND = new FarmingKindWording();

  @Override
  public boolean showsUses() {
    return false;
  }

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    if ("PAYBACK".equals(detail.label())) {
      return new String[] {
        LoopWording.clock(detail.seconds()),
        "-",
        "paid back",
        "net %s gold per min since the purchase against a cost of %s"
            .formatted(LoopWording.gold(f.get("netGoldPerMinute")), LoopWording.gold(row.cost()))
      };
    }
    if ("DAMAGE".equals(detail.label())) {
      return damageDetail(detail);
    }
    if (SavingCostLine.LABEL.equals(detail.label())) {
      return savingDetail(row, detail);
    }
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "-",
      "%d creeps (%s per min), creep gold %s per min, camps %s per min"
          .formatted(
              Math.round(f.get("kills")),
              ItemWording.number(f.get("killsPerMinute")),
              LoopWording.gold(f.get("goldPerMinute")),
              ItemWording.number(f.get("campsPerMinute"))),
      "%s minutes %s the first purchase"
          .formatted(
              ItemWording.number(f.get("minutes")),
              "BEFORE".equals(detail.label()) ? "before" : "after")
    };
  }

  private static String[] savingDetail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "-",
      SavingCostLine.of(row).orElse("-"),
      ("damage per min %s (%s in the window before), kill participation per min %s (%s before);"
              + " gold gain %s")
          .formatted(
              LoopWording.gold(f.get("damagePerMinute")),
              LoopWording.gold(f.get("damagePerMinuteBefore")),
              ItemWording.number(f.get("participationPerMinute")),
              ItemWording.number(f.get("participationPerMinuteBefore")),
              LoopWording.gold(f.get("gain.saving-cost")))
    };
  }

  private static String[] damageDetail(Detail detail) {
    Map<String, Double> f = detail.figures();
    String stats =
        f.containsKey("bonusDamage") || f.containsKey("bonusAttackSpeed")
            ? "; its stats: +%s damage, +%s attack speed"
                .formatted(
                    LoopWording.gold(f.getOrDefault("bonusDamage", 0.0)),
                    LoopWording.gold(f.getOrDefault("bonusAttackSpeed", 0.0)))
            : "";
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "enemy heroes",
      ("%s damage from the item's own effects and %s more from its stats, worth %s gold by kill"
              + " share, the item's own damage helped %s kills")
          .formatted(
              LoopWording.gold(f.get("procDamage")),
              LoopWording.gold(f.get("passiveDamage")),
              LoopWording.gold(f.get("damageValue")),
              LoopWording.gold(f.getOrDefault("killsHelped", 0.0))),
      ("hero damage per min %s before the purchase and %s after (the stage of the game also raises"
              + " it)%s")
          .formatted(
              LoopWording.gold(f.get("beforePerMinute")),
              LoopWording.gold(f.get("afterPerMinute")),
              stats)
    };
  }
}
