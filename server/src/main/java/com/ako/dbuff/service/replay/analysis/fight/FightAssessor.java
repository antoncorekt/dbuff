package com.ako.dbuff.service.replay.analysis.fight;

import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Says where a detected fight was, who won it and how each player took part, from the players'
 * sampled positions and what the activity classifier made of them.
 */
public final class FightAssessor {
  private static final int PLAYERS = 10;
  private static final String RADIANT = "RADIANT";
  private static final String DIRE = "DIRE";
  private static final String DRAW = "DRAW";
  private static final List<String> TELEPORTS =
      List.of("item_tpscroll", "item_travel_boots", "item_travel_boots_2");
  private static final Set<Presence> AWAY_BUT_ALIVE =
      EnumSet.of(
          Presence.ABSENT_FAR,
          Presence.ABSENT_FARMING,
          Presence.ABSENT_BASE,
          Presence.ABSENT_PUSHING);

  private final int participationRadius;
  private final int winMarginPercent;

  public FightAssessor(int participationRadius, int winMarginPercent) {
    this.participationRadius = participationRadius;
    this.winMarginPercent = winMarginPercent;
  }

  /** One player's samples; level 0 means no data, flag bit 0 means alive. */
  public record Trace(
      int startSeconds, int stepSeconds, int[] x, int[] y, int[] level, int[] flags) {

    public int indexAt(double second) {
      int i = (int) Math.round((second - startSeconds) / stepSeconds);
      return Math.max(0, Math.min(x.length - 1, i));
    }
  }

  /**
   * @param distanceToCenter null when the hero or the centre has no position
   */
  public record PlayerPart(
      int player,
      Presence presence,
      long damageDealt,
      long damageTaken,
      int deaths,
      Integer distanceToCenter,
      String activityAtStart,
      AbsenceReason absenceReason,
      Integer teleportCooldownLeft) {}

  /**
   * @param centerX null when no participant had a position
   */
  public record Assessment(
      Integer centerX,
      Integer centerY,
      String winner,
      int radiantDeaths,
      int direDeaths,
      int radiantParticipants,
      int direParticipants,
      long radiantDamage,
      long direDamage,
      List<PlayerPart> parts) {}

  public Assessment assess(
      FightDetector.Detected fight,
      Map<Integer, Trace> traces,
      Map<Integer, List<ActivitySegment>> activities,
      Map<Integer, HeroStateTimeline> states) {
    double middle = (fight.start() + fight.end()) / 2;
    int[] center = center(fight, traces, middle);
    int radiantDeaths = 0;
    int direDeaths = 0;
    long levelLostByRadiant = 0;
    long levelLostByDire = 0;
    for (FightDetector.Death death : fight.deaths()) {
      long level = Math.max(1, levelAt(traces.get(death.victim()), death.second()));
      if (FightDetector.team(death.victim()) == 0) {
        radiantDeaths++;
        levelLostByRadiant += level;
      } else {
        direDeaths++;
        levelLostByDire += level;
      }
    }
    long radiantDamage = teamDamage(fight, 0);
    long direDamage = teamDamage(fight, 1);
    String winner = winner(levelLostByDire, levelLostByRadiant, radiantDamage, direDamage);
    List<PlayerPart> parts = new ArrayList<>();
    for (int player = 0; player < PLAYERS; player++) {
      parts.add(
          part(
              player,
              fight,
              traces.get(player),
              activities.getOrDefault(player, List.of()),
              states.getOrDefault(player, new HeroStateTimeline()),
              center));
    }
    return new Assessment(
        center == null ? null : center[0],
        center == null ? null : center[1],
        winner,
        radiantDeaths,
        direDeaths,
        (int) fight.participants().stream().filter(p -> FightDetector.team(p) == 0).count(),
        (int) fight.participants().stream().filter(p -> FightDetector.team(p) == 1).count(),
        radiantDamage,
        direDamage,
        parts);
  }

  private static int[] center(
      FightDetector.Detected fight, Map<Integer, Trace> traces, double second) {
    long x = 0;
    long y = 0;
    int count = 0;
    for (int player : fight.participants()) {
      Trace trace = traces.get(player);
      if (trace == null) {
        continue;
      }
      int i = trace.indexAt(second);
      if (trace.level()[i] > 0) {
        x += trace.x()[i];
        y += trace.y()[i];
        count++;
      }
    }
    return count == 0 ? null : new int[] {(int) (x / count), (int) (y / count)};
  }

  private static long levelAt(Trace trace, double second) {
    return trace == null ? 0 : trace.level()[trace.indexAt(second)];
  }

  private static long teamDamage(FightDetector.Detected fight, int team) {
    return fight.damageDealt().entrySet().stream()
        .filter(e -> FightDetector.team(e.getKey()) == team)
        .mapToLong(Map.Entry::getValue)
        .sum();
  }

  /** By the levels of the heroes killed, then by damage, and a draw when neither is clear. */
  private String winner(long radiantScore, long direScore, long radiantDamage, long direDamage) {
    String byDeaths = lead(radiantScore, direScore);
    return byDeaths != null ? byDeaths : orDraw(lead(radiantDamage, direDamage));
  }

  private String lead(long radiant, long dire) {
    long total = radiant + dire;
    if (total == 0 || Math.abs(radiant - dire) * 100 < winMarginPercent * total) {
      return null;
    }
    return radiant > dire ? RADIANT : DIRE;
  }

  private static String orDraw(String winner) {
    return winner == null ? DRAW : winner;
  }

  private PlayerPart part(
      int player,
      FightDetector.Detected fight,
      Trace trace,
      List<ActivitySegment> segments,
      HeroStateTimeline state,
      int[] center) {
    long dealt = fight.damageDealt().getOrDefault(player, 0L);
    long taken = fight.damageTaken().getOrDefault(player, 0L);
    int deaths = (int) fight.deaths().stream().filter(d -> d.victim() == player).count();
    Integer distance = distanceAtStart(trace, fight.start(), center);
    ActivitySegment atStart = segmentAt(segments, fight.start());
    String activity = atStart == null ? null : atStart.activity().name();
    Presence presence = presence(player, fight, trace, atStart, center);
    boolean couldHaveTeleported = AWAY_BUT_ALIVE.contains(presence);
    return new PlayerPart(
        player,
        presence,
        dealt,
        taken,
        deaths,
        distance,
        activity,
        couldHaveTeleported ? absenceReason(state, fight.start()) : null,
        couldHaveTeleported ? teleportCooldownLeft(state, fight.start()) : null);
  }

  private static AbsenceReason absenceReason(HeroStateTimeline state, double second) {
    if (TELEPORTS.stream().noneMatch(t -> state.owns(t, second))) {
      return AbsenceReason.NO_TP;
    }
    return teleportCooldownLeft(state, second) > 0
        ? AbsenceReason.TP_ON_COOLDOWN
        : AbsenceReason.HAD_TP_NOT_USED;
  }

  /** The shortest wait among the teleports the hero held, null when he held none. */
  private static Integer teleportCooldownLeft(HeroStateTimeline state, double second) {
    return TELEPORTS.stream()
        .filter(t -> state.owns(t, second))
        .mapToInt(t -> (int) Math.ceil(state.cooldownLeft(t, second)))
        .boxed()
        .min(Integer::compare)
        .orElse(null);
  }

  private Presence presence(
      int player,
      FightDetector.Detected fight,
      Trace trace,
      ActivitySegment atStart,
      int[] center) {
    if (fight.participants().contains(player)) {
      return Presence.ACTIVE;
    }
    if (trace == null || trace.level()[trace.indexAt(fight.start())] == 0) {
      return Presence.ABSENT_FAR;
    }
    if ((trace.flags()[trace.indexAt(fight.start())] & 1) == 0) {
      return Presence.ABSENT_DEAD;
    }
    if (atStart != null) {
      Presence reason = reason(atStart.activity());
      if (reason != null) {
        return reason;
      }
    }
    return wasNear(trace, fight, center) ? Presence.PRESENT_PASSIVE : Presence.ABSENT_FAR;
  }

  private static Presence reason(Activity activity) {
    return switch (activity) {
      case DEAD -> Presence.ABSENT_DEAD;
      case TELEPORTING -> Presence.ABSENT_TELEPORTING;
      case JUNGLE_FARM, LANE_FARM -> Presence.ABSENT_FARMING;
      case IN_BASE -> Presence.ABSENT_BASE;
      case PUSHING -> Presence.ABSENT_PUSHING;
      default -> null;
    };
  }

  private boolean wasNear(Trace trace, FightDetector.Detected fight, int[] center) {
    if (center == null) {
      return false;
    }
    for (int i = trace.indexAt(fight.start()); i <= trace.indexAt(fight.end()); i++) {
      if (trace.level()[i] > 0
          && Math.hypot(trace.x()[i] - center[0], trace.y()[i] - center[1])
              <= participationRadius) {
        return true;
      }
    }
    return false;
  }

  private static Integer distanceAtStart(Trace trace, double start, int[] center) {
    if (trace == null || center == null) {
      return null;
    }
    int i = trace.indexAt(start);
    if (trace.level()[i] == 0) {
      return null;
    }
    return (int) Math.round(Math.hypot(trace.x()[i] - center[0], trace.y()[i] - center[1]));
  }

  private static ActivitySegment segmentAt(List<ActivitySegment> segments, double second) {
    return segments.stream()
        .filter(s -> second >= s.startSeconds() && second < s.endSeconds())
        .findFirst()
        .orElse(null);
  }
}
