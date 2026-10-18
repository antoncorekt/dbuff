package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.TargetUseKindWording;
import java.util.Map;
import java.util.function.Function;

/** Items cast on a hero: whom each cast saved, which kills it set up. */
final class TargetUseWording implements Wording {
  private static final TargetUseKindWording KIND = new TargetUseKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    String evidence;
    if (f.getOrDefault("saved", 0.0) == 1.0) {
      evidence =
          "a save, health %s of %s, %s damage within 3 s, worth %s"
              .formatted(
                  LoopWording.gold(f.get("hpBefore")),
                  LoopWording.gold(f.get("maxHp")),
                  LoopWording.gold(f.get("damageAround")),
                  LoopWording.gold(f.get("saveGain")));
    } else if (f.getOrDefault("setUp", 0.0) == 1.0) {
      evidence = "the enemy died soon after, worth " + LoopWording.gold(f.get("controlGain"));
    } else if (f.getOrDefault("effective", 1.0) == 0.0) {
      evidence = "no effect on the target";
    } else {
      evidence = "no save, no kill";
    }
    return new String[] {
      LoopWording.clock(detail.seconds()),
      TargetUseKindWording.hero(detail.target()),
      "cast",
      evidence
    };
  }
}
