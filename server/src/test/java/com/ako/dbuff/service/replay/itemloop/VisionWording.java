package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.Prettify;
import com.ako.dbuff.service.replay.report.VisionKindWording;
import java.util.function.Function;

/** Wards, sentries, dust and the gem: the facts and the kills and dewards they gave. */
final class VisionWording implements Wording {
  private static final VisionKindWording KIND = new VisionKindWording();

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
    boolean kill = VisionKindWording.INVISIBLE_KILL.equals(detail.label());
    return new String[] {
      LoopWording.clock(detail.seconds()),
      detail.target() == null
          ? "-"
          : kill ? Prettify.unit(detail.target()) : LoopWording.words(detail.target()),
      kill ? "invisible enemy killed where the item gave sight" : "enemy ward removed under sentry",
      kill
          ? "kill worth %s, item share %s"
              .formatted(
                  LoopWording.gold(detail.figures().get("killValue")),
                  LoopWording.gold(detail.figures().get("killShare")))
          : "bounty %s".formatted(LoopWording.gold(detail.figures().get("bounty")))
    };
  }
}
