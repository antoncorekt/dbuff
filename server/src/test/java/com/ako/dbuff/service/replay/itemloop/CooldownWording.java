package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.CooldownKindWording;
import java.util.Map;
import java.util.function.Function;

/** Cooldown reduction: extra casts, the kills they helped and the worth. */
final class CooldownWording implements Wording {
  private static final CooldownKindWording KIND = new CooldownKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    return new String[] {
      LoopWording.clock(detail.seconds()),
      detail.target() == null ? "-" : detail.target(),
      "cast %s after the last, base cooldown %s"
          .formatted(LoopWording.clock(f.get("gap")), LoopWording.clock(f.get("baseCooldown"))),
      f.get("goldValue") != null && f.get("goldValue") > 0
          ? "gave %s gold".formatted(LoopWording.gold(f.get("goldValue")))
          : "-"
    };
  }
}
