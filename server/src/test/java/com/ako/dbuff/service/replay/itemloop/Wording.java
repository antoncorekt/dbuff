package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * How the report words the rows of one evaluator kind. A new kind needs its entry in {@link
 * #FOR_KIND} to read well; without one the generic wording shows its numbers.
 */
interface Wording {
  Map<String, Wording> FOR_KIND =
      Map.of(
          "magic-stick", new StickWording(),
          "hand-of-midas", new MidasWording(),
          "farming-group", new FarmingWording());

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

  /** What the flags of a row say the data could not tell, in words. */
  static String limits(List<String> flags) {
    if (flags.isEmpty()) {
      return "";
    }
    return " (" + flags.stream().map(Wording::flagWords).collect(Collectors.joining("; ")) + ")";
  }

  private static String flagWords(String flag) {
    return switch (flag) {
      case "HP_UNCAPPED" ->
          "his max health could not be estimated for some uses, so every charge counts as"
              + " health restored";
      case "MANA_UNCAPPED" -> "the track has no mana, so every charge counts as mana restored";
      case "NO_CHARGES_SEEN" -> "a use with no charge count seen counts as 0 charges";
      case "COOLDOWN_ESTIMATED" ->
          "the replay shows no cooldown for it, so the ready time is the use plus the cooldown"
              + " guess";
      case "EXTRA_ESTIMATED" -> "what a normal kill pays is estimated from the creep values";
      case "NO_DAMAGE_COMPARISON" ->
          "bought too early or too late to compare his damage before and after, so only the"
              + " item's own damage counts";
      case "COST_UNKNOWN" -> "the item constants give no cost for it, so there is no ratio";
      case "BONUS_GOLD_UNKNOWN" -> "the constants give no bonus gold, so no gold is counted";
      case "USE_WITHOUT_GOLD" -> "a use had no gold entry in the log";
      case "NO_BEFORE_WINDOW" -> "bought too early for a before window, not compared";
      case "SHORT_AFTER_WINDOW" -> "bought too late for an after window, not compared";
      default -> LoopWording.words(flag);
    };
  }

  static String plural(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }

  static String number(Double value) {
    if (value == null) {
      return "?";
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

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
