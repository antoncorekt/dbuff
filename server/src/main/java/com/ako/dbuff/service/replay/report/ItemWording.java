package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The phrases the item kinds share: what the data could not tell, counts, numbers and times. The
 * wording of one kind is its {@link ItemKindWording}.
 */
public final class ItemWording {
  private static final String UNKNOWN = "?";

  private ItemWording() {}

  /** Whether the figures hold a value, not null, for every key. */
  public static boolean has(Map<String, Double> figures, String... keys) {
    return figures != null && java.util.Arrays.stream(keys).allMatch(k -> figures.get(k) != null);
  }

  /** A figure, 0 when it is missing or null. */
  public static double figure(Map<String, Double> figures, String key) {
    Double value = figures == null ? null : figures.get(key);
    return value == null ? 0 : value;
  }

  /** The ratio as {@code +0.3x}, or {@code -0.3x}. */
  public static String signedRatio(double ratio) {
    return (ratio >= 0 ? "+" : "") + ratio(ratio);
  }

  /** {@code 1.5x its cost} when the cost is known, nothing otherwise. */
  public static String worth(ItemEfficiency row) {
    return row.ratio() == null ? "" : ratio(row.ratio()) + " its cost";
  }

  /** What the flags of a row say the data could not tell, in words, in brackets. */
  public static String limits(List<String> flags) {
    if (flags.isEmpty()) {
      return "";
    }
    return " ("
        + flags.stream().map(ItemWording::flagWords).collect(Collectors.joining("; "))
        + ")";
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
      case "DEATH_COST_ESTIMATED" ->
          "the gold lost or the respawn time of the saved death is estimated";
      case "NO_GOLD_TRACK" -> "the track has no gold, so the cost of saving up is not counted";
      case "NO_MINUTE_GOLD" ->
          "the match has no gold per minute for a death while saving, so its cost counts as 0";
      case "NEVER_PLACED" -> "he bought it and never placed one";
      case "DUST_UNUSED" -> "he bought dust and never used it";
      case "DEWARDED_FAST" -> "an observer of his was destroyed within a minute";
      case "COST_UNKNOWN" -> "the item constants give no cost for it, so there is no ratio";
      case "BONUS_GOLD_UNKNOWN" -> "the constants give no bonus gold, so no gold is counted";
      case "USE_WITHOUT_GOLD" -> "a use had no gold entry in the log";
      case "NO_BEFORE_WINDOW" -> "bought too early for a before window, not compared";
      case "SHORT_AFTER_WINDOW" -> "bought too late for an after window, not compared";
      default -> BehaviourWording.words(flag);
    };
  }

  public static String plural(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }

  public static String number(Double value) {
    return value == null ? UNKNOWN : String.format(Locale.ROOT, "%.1f", value);
  }

  /** What the item gave divided by what it cost, as {@code 1.5x}; {@code -} when not known. */
  public static String ratio(Double ratio) {
    return ratio == null ? "-" : String.format(Locale.ROOT, "%.1fx", ratio);
  }

  /** Game seconds as {@code m:ss}, {@code ?} when not known. */
  public static String clock(Double seconds) {
    return seconds == null ? UNKNOWN : GameTime.clock(seconds);
  }
}
