package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.SoulRingKindWording;
import java.util.Map;
import java.util.function.Function;

/** Soul Ring: mana gained, health spent, and the deaths it caused. */
final class SoulRingWording implements Wording {
  private static final SoulRingKindWording KIND = new SoulRingKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    if (f.containsKey("useSecond")) {
      return new String[] {
        LoopWording.clock(detail.seconds()),
        "-",
        "a death it caused, worth " + LoopWording.gold(f.get("selfHarmGain")),
        "used at %s with %s HP, %s damage since"
            .formatted(
                LoopWording.clock(f.get("useSecond")),
                LoopWording.gold(f.get("hpBefore")),
                LoopWording.gold(f.get("damageSince")))
      };
    }
    return new String[] {
      LoopWording.clock(detail.seconds()) + (f.get("laning") == 1.0 ? " (laning)" : ""),
      "-",
      "%s HP, %s mana".formatted(LoopWording.gold(f.get("hp")), LoopWording.gold(f.get("mana"))),
      "-"
    };
  }
}
