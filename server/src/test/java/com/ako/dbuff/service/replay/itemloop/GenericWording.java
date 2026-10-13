package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import java.util.function.Function;

/** A kind the report has no wording for yet: its totals as they are. */
final class GenericWording implements Wording {
  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return "%s, gain %s, cost %s%s"
        .formatted(
            Wording.plural(row.count(), "use"),
            LoopWording.gold(row.gain()),
            LoopWording.gold(row.cost()),
            Wording.limits(row.flags()));
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    return new String[] {
      LoopWording.clock(detail.seconds()),
      detail.target() == null ? "-" : Wording.creep(detail.target()),
      LoopWording.words(detail.label()),
      detail.figures().toString()
    };
  }
}
