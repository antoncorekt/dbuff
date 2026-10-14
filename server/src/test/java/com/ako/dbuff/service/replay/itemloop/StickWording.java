package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.ItemWording;
import com.ako.dbuff.service.replay.report.StickKindWording;
import java.util.Map;
import java.util.function.Function;

/** Magic Stick and Wand: health and mana restored, saves, deaths soon after a use. */
final class StickWording implements Wording {
  private static final StickKindWording KIND = new StickKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    String health =
        f.containsKey("hpBefore") && f.containsKey("maxHp")
            ? "health %s of %s"
                .formatted(LoopWording.gold(f.get("hpBefore")), LoopWording.gold(f.get("maxHp")))
            : f.containsKey("hpBefore")
                ? "health " + LoopWording.gold(f.get("hpBefore"))
                : "health unknown";
    String verdict =
        f.get("saved") == 1.0
            ? "a save, he lived"
            : f.get("diedSoon") == 1.0 ? "he died within 10 s" : "no danger";
    return new String[] {
      LoopWording.clock(detail.seconds()) + (f.get("laning") == 1.0 ? " (laning)" : ""),
      "-",
      "%s HP, %s mana (%s)"
          .formatted(
              LoopWording.gold(f.get("hp")),
              LoopWording.gold(f.get("mana")),
              ItemWording.plural(Math.round(f.get("charges")), "charge")),
      "%s, %s damage within 3 s, %s"
          .formatted(health, LoopWording.gold(f.get("damageAround")), verdict)
    };
  }
}
