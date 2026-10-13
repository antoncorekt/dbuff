package com.ako.dbuff.service.replay.looprunner;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import com.ako.dbuff.service.replay.report.Prettify;
import com.ako.dbuff.service.replay.report.ReasonWording;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How every review-loop report writes a value: plain words, time as m:ss, gold as "1.2k", teams as
 * Radiant and Dire, a hero by its name, a reason as a phrase. No internal code reaches a table cell
 * through here.
 */
public final class LoopWording {
  private static final String UNKNOWN = "?";
  private static final long THOUSAND = 1000;
  private static final List<String> ROLES =
      List.of("carry", "mid", "offlane", "soft support", "hard support");
  private static final int BEST_FITS = 3;

  private LoopWording() {}

  /** Game seconds as {@code m:ss}, {@code ?} when not known. */
  public static String clock(Number seconds) {
    if (seconds == null) {
      return UNKNOWN;
    }
    long whole = Math.round(seconds.doubleValue());
    long abs = Math.abs(whole);
    return "%s%d:%02d".formatted(whole < 0 ? "-" : "", abs / 60, abs % 60);
  }

  /** Gold as {@code 850} or {@code 1.2k}, {@code ?} when not known. */
  public static String gold(Number gold) {
    if (gold == null) {
      return UNKNOWN;
    }
    long whole = Math.round(gold.doubleValue());
    if (Math.abs(whole) < THOUSAND) {
      return String.valueOf(whole);
    }
    String thousands = String.format(Locale.ROOT, "%.1f", whole / (double) THOUSAND);
    return (thousands.endsWith(".0") ? thousands.substring(0, thousands.length() - 2) : thousands)
        + "k";
  }

  /** A Radiant lead as {@code Radiant +2.3k}; a negative one as {@code Dire +1.1k}. */
  public static String advantage(Number radiantLead) {
    if (radiantLead == null || Double.isNaN(radiantLead.doubleValue())) {
      return UNKNOWN;
    }
    long whole = Math.round(radiantLead.doubleValue());
    if (whole == 0) {
      return "even";
    }
    return "%s +%s".formatted(whole > 0 ? "Radiant" : "Dire", gold(Math.abs(whole)));
  }

  /** A gain of a team as {@code Radiant +1.8k}, or {@code Radiant -400} when it lost ground. */
  public static String change(String team, Number gain) {
    if (gain == null) {
      return UNKNOWN;
    }
    long whole = Math.round(gain.doubleValue());
    return "%s %s%s".formatted(team(team), whole < 0 ? "-" : "+", gold(Math.abs(whole)));
  }

  /** {@code RADIANT} as {@code Radiant}; a team that is not known as {@code ?}. */
  public static String team(String stored) {
    if (stored == null) {
      return UNKNOWN;
    }
    return switch (stored.toUpperCase(Locale.ROOT)) {
      case "RADIANT" -> "Radiant";
      case "DIRE" -> "Dire";
      default -> UNKNOWN;
    };
  }

  public static String team(TeamSide side) {
    return side == null ? UNKNOWN : team(side.name());
  }

  public static String teamOfSlot(long slot) {
    return team(TeamSide.ofSlot(slot));
  }

  /** The hero of a slot; without a known hero, "Dire player 2", never an id. */
  public static String hero(Map<Long, String> heroBySlot, Long slot) {
    if (slot == null) {
      return UNKNOWN;
    }
    String name = heroBySlot.get(slot);
    if (name != null && !name.isBlank()) {
      return name;
    }
    long number = slot >= TeamSide.FIRST_DIRE_SLOT ? slot - TeamSide.FIRST_DIRE_SLOT : slot;
    return "%s player %d".formatted(teamOfSlot(slot), number + 1);
  }

  /** A stored upper-case name as words: {@code FIGHT_WIN} as {@code fight win}. */
  public static String words(String stored) {
    return stored == null ? UNKNOWN : stored.toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  /** A role 1 to 5 as {@code 1 carry}; anything else as {@code unknown}. */
  public static String role(Integer role) {
    return role == null || role < 1 || role > ROLES.size()
        ? "unknown"
        : role + " " + ROLES.get(role - 1);
  }

  /**
   * The three best scores of a comma separated list of the roles 1 to 5 as {@code carry 1.50, mid
   * 0.20}; {@code unknown} when the list is empty.
   */
  public static String bestFits(String roleScores) {
    return bestFits(roleScores, BEST_FITS);
  }

  /** The best {@code limit} scores, at most one per role, highest first. */
  public static String bestFits(String roleScores, int limit) {
    if (roleScores == null || roleScores.isBlank()) {
      return "unknown";
    }
    String[] parts = roleScores.split(",");
    return IntStream.range(0, Math.min(parts.length, ROLES.size()))
        .boxed()
        .sorted(Comparator.comparingDouble(i -> -Double.parseDouble(parts[i])))
        .limit(limit)
        .map(i -> "%s %s".formatted(ROLES.get(i), parts[i].strip()))
        .collect(Collectors.joining(", "));
  }

  /** The stored composition tokens in plain words, {@code fallback} when there are none. */
  public static String tokens(String stored, String fallback) {
    if (stored == null || stored.isBlank()) {
      return fallback;
    }
    return Arrays.stream(stored.split(","))
        .map(String::strip)
        .filter(token -> !token.isEmpty())
        .map(CompositionToken::words)
        .collect(Collectors.joining(", "));
  }

  /** A reason as a sentence about the hero, for example "Mirana stood too far in front". */
  public static String reason(FightReasonDomain reason, String hero) {
    return ReasonWording.phrase(reason, hero);
  }

  /** What a stored code says about the hero, without the hero: "had no teleport". */
  public static String reasonWithoutHero(String code, String hero) {
    FightReasonDomain bare =
        FightReasonDomain.builder().scope("PLAYER").code(code).evidence("").build();
    String sentence = reason(bare, hero);
    return sentence.startsWith(hero + " ") ? sentence.substring(hero.length() + 1) : sentence;
  }

  /** OpenDota's lane role of a player in words; a number it does not know is shown as it is. */
  public static String laneRole(Long laneRole) {
    if (laneRole == null) {
      return UNKNOWN;
    }
    return switch (laneRole.intValue()) {
      case 1 -> "safe lane";
      case 2 -> "mid lane";
      case 3 -> "offlane";
      case 4 -> "jungle";
      default -> "lane role " + laneRole;
    };
  }

  /** A stored rune type in words: {@code DOUBLE_DAMAGE} as {@code double damage}. */
  public static String rune(String stored) {
    RuneType type = Enums.find(RuneType.class, stored);
    return type == null ? UNKNOWN : type.words();
  }

  /** Where a hero stood: {@code mid lane}, {@code river}, {@code jungle}, {@code base}. */
  public static String place(String zone, String lane) {
    if (zone == null) {
      return "unknown";
    }
    return "LANE".equals(zone) && lane != null ? words(lane) + " lane" : words(zone);
  }

  /** A drafted or used ability by its readable name. */
  public static String ability(String internalName) {
    return Prettify.ability(internalName);
  }
}
