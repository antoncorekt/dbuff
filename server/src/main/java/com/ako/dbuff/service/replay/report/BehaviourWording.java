package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationReason;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The words of the team rotations and smoke uses that the Discord sections and the behaviour loop
 * report share.
 */
public final class BehaviourWording {
  private static final String UNKNOWN = "?";
  private static final long THOUSAND = 1000;

  private BehaviourWording() {}

  /** A stored upper-case name as words: {@code FIGHT_WIN} as {@code fight win}. */
  public static String words(String stored) {
    return stored == null ? UNKNOWN : stored.toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  /** Where a hero stood: {@code mid lane}, {@code river}, {@code jungle}, {@code base}. */
  public static String place(String zone, String lane) {
    if (zone == null) {
      return "unknown";
    }
    return "LANE".equals(zone) && lane != null ? words(lane) + " lane" : words(zone);
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
    return "%s player %d".formatted(Prettify.team(TeamSide.ofSlot(slot).name()), number + 1);
  }

  /** {@code Radiant}, or {@code Radiant, your team} when the team is the one being reported to. */
  public static String teamTag(String stored, TeamSide yours) {
    TeamSide side = Enums.find(TeamSide.class, stored);
    return Prettify.team(stored) + (side != null && side == yours ? ", your team" : "");
  }

  /** The reasons stored on a rotation, in stored order; codes that are not known are left out. */
  public static List<RotationReason> reasons(TeamRotationDomain rotation) {
    if (rotation.getReasons() == null || rotation.getReasons().isBlank()) {
      return List.of();
    }
    return Arrays.stream(rotation.getReasons().split(","))
        .map(code -> Enums.find(RotationReason.class, code))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * One reason of a rotation as a sentence. {@code detailed} adds what "did nothing" was checked
   * against.
   */
  public static String rotationSentence(
      RotationReason reason, String hero, TeamRotationDomain r, boolean detailed) {
    return switch (reason) {
      case DIED -> "%s died on the trip".formatted(hero);
      case DID_NOTHING ->
          detailed
              ? ("%s did nothing there (no fight, no damage to enemy heroes or towers, no kill, no"
                      + " creeps)")
                  .formatted(hero)
              : "%s did nothing there".formatted(hero);
      case INCOME_DROP ->
          "%s's farm dropped from %s to %s gold per minute (experience from %s to %s per minute)"
              .formatted(
                  hero,
                  gold(r.getIncomeBefore()),
                  gold(r.getIncomeDuring()),
                  gold(r.getXpBefore()),
                  gold(r.getXpDuring()));
      case ARRIVED_AFTER_FIGHT_ENDED ->
          "%s arrived after the fight in that lane was over".formatted(hero);
      case LEFT_LANE_UNDEFENDED ->
          "the lane %s left lost the creep count (his partners %s, the enemy %s)"
              .formatted(hero, count(r.getProxyOwnKills()), count(r.getProxyEnemyKills()));
      case TOWER_LOST_WHILE_AWAY -> "an own tower fell while %s was away".formatted(hero);
    };
  }

  /** The heroes of a smoke's group, as stored; a slot that is not a number is left out. */
  public static List<Long> groupSlots(String stored) {
    if (stored == null) {
      return List.of();
    }
    return Arrays.stream(stored.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .map(BehaviourWording::slot)
        .filter(Objects::nonNull)
        .toList();
  }

  /** The names of the group's heroes, comma separated. */
  public static String group(SmokeUseDomain smoke, Map<Long, String> heroNames) {
    return groupSlots(smoke.getGroupSlots()).stream()
        .map(slot -> hero(heroNames, slot))
        .collect(Collectors.joining(", "));
  }

  /** Where a smoke's group went, from the zone at its start to the zone at its end. */
  public static String smokeWhere(SmokeUseDomain s) {
    boolean startKnown = s.getStartZone() != null;
    boolean endKnown = s.getEndZone() != null;
    String start = place(s.getStartZone(), s.getStartLane());
    String end = place(s.getEndZone(), s.getEndLane());
    if (!startKnown || !endKnown) {
      return startKnown
          ? "started in the %s, end not known".formatted(start)
          : endKnown ? "start not known, ended in the %s".formatted(end) : "not known";
    }
    return start.equals(end)
        ? "stayed in the " + start
        : "from the %s to the %s".formatted(start, end);
  }

  private static String count(Integer count) {
    return count == null ? UNKNOWN : String.valueOf(count);
  }

  private static Long slot(String text) {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
