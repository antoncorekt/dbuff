package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.report.ItemWording;
import com.ako.dbuff.service.replay.report.TeamRestoreKindWording;
import java.util.Map;
import java.util.function.Function;

/** Arcane Boots, Mekansm, Guardian Greaves, Holy Locket: health and mana to allies, saves. */
final class TeamRestoreWording implements Wording {
  private static final TeamRestoreKindWording KIND = new TeamRestoreKindWording();

  @Override
  public String result(ItemEfficiency row, Function<String, String> itemNames) {
    return KIND.longVerdict(row);
  }

  @Override
  public String[] detail(ItemEfficiency row, Detail detail) {
    Map<String, Double> f = detail.figures();
    double saved = ItemWording.figure(f, "saved");
    double died = ItemWording.figure(f, "diedSoon");
    return new String[] {
      LoopWording.clock(detail.seconds()),
      TeamRestoreKindWording.allies(Math.round(ItemWording.figure(f, "allies"))),
      "%s HP, %s mana"
          .formatted(
              LoopWording.gold(ItemWording.figure(f, "hp")),
              LoopWording.gold(ItemWording.figure(f, "mana"))),
      saved > 0
          ? ItemWording.plural(Math.round(saved), "save")
          : died > 0
              ? TeamRestoreKindWording.allies(Math.round(died)) + " died within 10 s"
              : "no danger"
    };
  }
}
