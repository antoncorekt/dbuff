package com.ako.dbuff.service.replay.report;

import java.util.Map;
import java.util.Optional;

/**
 * The words of one hero's stored skill effect figures, shared by the report section and the review
 * documents. The numbers are counts of judged casts, so a line says only what they show: a trap
 * count is a floor because the shard centre is estimated, and a hit in the dark is counted as the
 * data has it.
 */
public final class SkillEffectWording {
  public static final String FALSE_PROMISE = "false_promise";
  public static final String MEAT_HOOK = "meat_hook";
  public static final String ICE_SHARDS = "ice_shards";

  private static final int PERCENT = 100;

  private SkillEffectWording() {}

  /**
   * The line of one hero and catalog entry; empty for an entry with no wording or with nothing to
   * say.
   *
   * @param entryKey the catalog entry
   * @param hero the hero as the report names him
   * @param skill the skill's name, used by the entries whose line names it
   * @param castCount casts of the hero
   * @param figures the outcome counts stored with the row
   */
  public static Optional<String> line(
      String entryKey, String hero, String skill, int castCount, Map<String, Integer> figures) {
    if (entryKey == null || figures == null) {
      return Optional.empty();
    }
    return switch (entryKey) {
      case MEAT_HOOK -> hook(hero, figures);
      case ICE_SHARDS -> trap(hero, skill, castCount, figures);
      case FALSE_PROMISE -> promise(hero, skill, figures);
      default -> Optional.empty();
    };
  }

  private static Optional<String> hook(String hero, Map<String, Integer> f) {
    int hit = count(f, "SEEN_HIT");
    int seen = hit + count(f, "SEEN_MISS");
    int dark = count(f, "DARK_HIT");
    int saved = count(f, "ALLY_SAVE");
    if (seen + dark + saved == 0) {
      return Optional.empty();
    }
    StringBuilder line = new StringBuilder(hero).append(": ");
    if (seen > 0) {
      line.append(
          "%d %s at seen enemies, %d hit (%d%%)"
              .formatted(seen, seen == 1 ? "hook" : "hooks", hit, percent(hit, seen)));
    } else {
      line.append("no hook at a seen enemy");
    }
    if (dark > 0) {
      line.append("; %d %s in the dark".formatted(dark, dark == 1 ? "hit" : "hits"));
    }
    if (saved > 0) {
      line.append("; %d %s saved".formatted(saved, saved == 1 ? "ally" : "allies"));
    }
    return Optional.of(line.append('.').toString());
  }

  private static Optional<String> trap(
      String hero, String skill, int casts, Map<String, Integer> f) {
    int trapped = count(f, "TRAP");
    int missed = count(f, "MISS");
    if (trapped + missed == 0) {
      return Optional.empty();
    }
    StringBuilder line = new StringBuilder(possessive(hero)).append(' ').append(skill);
    line.append(
        trapped > 0
            ? " trapped at least %d %s".formatted(trapped, trapped == 1 ? "enemy" : "enemies")
            : " trapped no enemy that could be told");
    line.append(" in %d %s".formatted(casts, casts == 1 ? "cast" : "casts"));
    if (missed > 0) {
      line.append("; %d %s missed a seen enemy".formatted(missed, missed == 1 ? "cast" : "casts"));
    }
    return Optional.of(line.append(" (the shard centre is estimated).").toString());
  }

  private static Optional<String> promise(String hero, String skill, Map<String, Integer> f) {
    int saved = count(f, "SAVE");
    int impact = count(f, "IMPACT");
    int chances = count(f, "MISSED");
    if (saved + impact + chances == 0) {
      return Optional.empty();
    }
    StringBuilder line = new StringBuilder(possessive(hero)).append(' ').append(skill);
    if (saved > 0) {
      line.append(" saved %d %s".formatted(saved, saved == 1 ? "ally" : "allies"));
      if (impact > 0) {
        line.append(", %d more died but dealt damage".formatted(impact));
      }
    } else if (impact > 0) {
      line.append(
          " saved no ally; %d %s died but dealt damage"
              .formatted(impact, impact == 1 ? "ally" : "allies"));
    } else {
      line.append(" saved no ally");
    }
    if (chances > 0) {
      line.append("; %d %s missed".formatted(chances, chances == 1 ? "chance" : "chances"));
    }
    return Optional.of(line.append('.').toString());
  }

  private static String possessive(String hero) {
    return hero.endsWith("s") ? hero + "'" : hero + "'s";
  }

  private static int count(Map<String, Integer> figures, String outcome) {
    Integer value = figures.get(outcome);
    return value == null ? 0 : Math.max(0, value);
  }

  private static int percent(int part, int whole) {
    return (int) Math.round(PERCENT * (double) part / whole);
  }
}
