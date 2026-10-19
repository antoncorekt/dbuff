package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.BottleKindWording;
import java.util.function.Function;

/** The Bottle: sips, health and mana restored, runes taken and bottled. */
final class BottleWording implements Wording {
  private static final BottleKindWording KIND = new BottleKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    if ("RUNE".equals(detail.label())) {
      boolean bottled = detail.figures().get("bottled") == 1.0;
      return new String[] {
        LoopWording.clock(detail.seconds()),
        detail.target(),
        bottled ? "bottled" : "drunk or not in the bottle",
        "-"
      };
    }
    return new String[] {
      LoopWording.clock(detail.seconds()),
      "-",
      "%s HP, %s mana"
          .formatted(
              LoopWording.gold(detail.figures().get("hp")),
              LoopWording.gold(detail.figures().get("mana"))),
      "-"
    };
  }
}
