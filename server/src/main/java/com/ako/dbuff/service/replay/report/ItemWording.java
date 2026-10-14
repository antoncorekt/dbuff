package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The words of the item efficiency rows that the Discord items section and the item loop report
 * share: what an item gave a hero, in plain words, and what the data could not tell.
 */
public final class ItemWording {
  public static final String MAGIC_STICK = "magic-stick";
  public static final String HAND_OF_MIDAS = "hand-of-midas";
  public static final String FARMING_GROUP = "farming-group";

  private static final String UNKNOWN = "?";

  private ItemWording() {}

  /** Whether the row is of a kind this class words. */
  public static boolean words(String kind) {
    return MAGIC_STICK.equals(kind) || HAND_OF_MIDAS.equals(kind) || FARMING_GROUP.equals(kind);
  }

  /** Whether the row has every figure its wording prints; without them it would say "?". */
  public static boolean complete(ItemEfficiency row) {
    if (row.figures() == null || row.flags() == null || row.rows() == null) {
      return false;
    }
    Map<String, Double> f = row.figures();
    return switch (row.kind()) {
      case MAGIC_STICK -> row.count() == 0 || has(f, "hp", "mana");
      case HAND_OF_MIDAS ->
          row.count() == 0 || has(f, "gold", "xp", "idleSeconds", "farmingSeconds");
      case FARMING_GROUP ->
          rates(row, "BEFORE")
              && rates(row, "AFTER")
              && (row.ratio() == null || has(f, "netGoldPerMinute"));
      default -> false;
    };
  }

  private static boolean rates(ItemEfficiency row, String label) {
    return row.rows().stream()
        .anyMatch(
            d ->
                d != null
                    && label.equals(d.label())
                    && d.figures() != null
                    && has(d.figures(), "killsPerMinute", "goldPerMinute", "campsPerMinute"));
  }

  private static boolean has(Map<String, Double> figures, String... keys) {
    return Arrays.stream(keys).allMatch(key -> figures.get(key) != null);
  }

  /** What a row of a known kind gave, with the limits of the data in brackets. */
  public static String result(ItemEfficiency row) {
    return switch (row.kind()) {
      case MAGIC_STICK -> stick(row);
      case HAND_OF_MIDAS -> midas(row);
      case FARMING_GROUP -> farming(row);
      default -> throw new IllegalArgumentException("No wording for " + row.kind());
    };
  }

  /** Magic Stick and Wand: health and mana restored, saves, deaths soon after a use. */
  public static String stick(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + limits(row.flags());
    }
    Map<String, Double> f = row.figures();
    long saves = Math.round(f.getOrDefault("saves", 0.0));
    long died = Math.round(f.getOrDefault("diedSoon", 0.0));
    return "%s, restored %s HP and %s mana, %s%s, worth %s its cost%s"
        .formatted(
            plural(row.count(), "use"),
            BehaviourWording.gold(f.get("hp")),
            BehaviourWording.gold(f.get("mana")),
            saves == 0 ? "no saves" : saves == 1 ? "1 save" : saves + " saves",
            died == 0 ? "" : ", died within 10 s after %d of them".formatted(died),
            ratio(row.ratio()),
            limits(row.flags()));
  }

  /** Hand of Midas: gold and experience of the uses, payback, idle time. */
  public static String midas(ItemEfficiency row) {
    if (row.count() == 0) {
      return "bought, never used" + limits(row.flags());
    }
    Map<String, Double> f = row.figures();
    double heroGold = f.getOrDefault("heroGold", 0.0);
    String share =
        heroGold > 0
            ? " (%d%% of his gold)".formatted(Math.round(100 * f.get("gold") / heroGold))
            : "";
    return ("%s, gave %s gold%s and %s XP, %s, ready but unused %s of the %s he farmed with"
            + " it, worth %s its cost%s")
        .formatted(
            plural(row.count(), "use"),
            BehaviourWording.gold(f.get("gold")),
            share,
            BehaviourWording.gold(f.get("xp")),
            row.paybackSeconds() == null
                ? "never paid for itself"
                : "paid for itself at " + clock(row.paybackSeconds()),
            clock(f.get("idleSeconds")),
            clock(f.get("farmingSeconds")),
            ratio(row.ratio()),
            limits(row.flags()) + attackSpeed(f));
  }

  /** The attack speed it also gives is named, not counted. */
  private static String attackSpeed(Map<String, Double> f) {
    return f.containsKey("bonusAttackSpeed")
        ? " (it also gives +%s attack speed, not counted)"
            .formatted(BehaviourWording.gold(f.get("bonusAttackSpeed")))
        : "";
  }

  /** The farming group: creeps, creep gold and camps per minute before and after the purchase. */
  public static String farming(ItemEfficiency row) {
    Map<String, Double> before = figures(row, "BEFORE");
    Map<String, Double> after = figures(row, "AFTER");
    String rates =
        "creeps %s per min before and %s after, creep gold %s and %s per min, camps %s and %s per min"
            .formatted(
                number(before.get("killsPerMinute")),
                number(after.get("killsPerMinute")),
                BehaviourWording.gold(before.get("goldPerMinute")),
                BehaviourWording.gold(after.get("goldPerMinute")),
                number(before.get("campsPerMinute")),
                number(after.get("campsPerMinute")));
    if (row.ratio() == null) {
      return rates + limits(row.flags());
    }
    String payback =
        row.paybackSeconds() == null
            ? "not paid back by the end"
            : "paid back at " + clock(row.paybackSeconds());
    String net = BehaviourWording.gold(row.figures().get("netGoldPerMinute"));
    if (row.figures().containsKey("damageValue") && row.cost() != null) {
      return "%s, net %s gold per min, %s, %s in total, %s%s"
          .formatted(rates, net, parts(row), ratio(row.ratio()), payback, limits(row.flags()));
    }
    return "%s, net %s gold per min, %s, worth %s its cost%s"
        .formatted(rates, net, payback, ratio(row.ratio()), limits(row.flags()));
  }

  /** The gold part and the damage part of the worth, each against the cost. */
  private static String parts(ItemEfficiency row) {
    Map<String, Double> f = row.figures();
    return "creeps %s its cost, damage %s"
        .formatted(
            signedRatio(f.getOrDefault("creepGain", 0.0) / row.cost()),
            signedRatio(f.get("damageValue") / row.cost()));
  }

  private static String signedRatio(double ratio) {
    return (ratio >= 0 ? "+" : "") + ratio(ratio);
  }

  private static Map<String, Double> figures(ItemEfficiency row, String label) {
    return row.rows().stream()
        .filter(d -> label.equals(d.label()))
        .findFirst()
        .map(Detail::figures)
        .orElse(Map.of("killsPerMinute", 0.0, "goldPerMinute", 0.0, "campsPerMinute", 0.0));
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
