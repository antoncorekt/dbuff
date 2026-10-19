package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.Map;
import java.util.function.Function;

/**
 * How the report words the rows of one main aspect. A new main aspect needs its entry in {@link
 * #FOR_KIND} to read well; without one the generic wording shows its numbers.
 */
interface Wording {
  Map<String, Wording> FOR_KIND =
      Map.of(
          "charge-restore", new StickWording(),
          "midas-uses", new MidasWording(),
          "farm-rate", new FarmingWording(),
          "cooldown", new CooldownWording(),
          "bottle-runes", new BottleWording());

  static Wording of(String kind) {
    return FOR_KIND.getOrDefault(kind, new GenericWording());
  }

  /** Whether the use count means something for the kind. */
  default boolean showsUses() {
    return true;
  }

  /** The row in words, with its limits of the data in brackets. */
  String result(ItemEfficiency row, Function<String, String> itemNames);

  /** The cells time, on what, what it gave and evidence of one detail row. */
  String[] detail(ItemEfficiency row, Detail detail);

  static String creep(String unit) {
    if (unit == null || unit.isBlank()) {
      return "-";
    }
    if (unit.startsWith("npc_dota_neutral_")) {
      return unit.substring("npc_dota_neutral_".length()).replace('_', ' ');
    }
    if (unit.contains("siege")) {
      return "siege creep";
    }
    String kind = unit.replaceFirst("npc_dota_creep_(goodguys|badguys)_", "").replace('_', ' ');
    return "lane " + kind + " creep";
  }
}
