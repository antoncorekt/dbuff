package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.AwayTrip;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what {@code team_rotation}
 * holds; the table headed {@link #ROTATIONS} is what {@link BehaviourLoopTopic} lets the loop
 * compare between rounds. The owner's verdict column is left empty.
 */
final class BehaviourLoopReport {
  static final String ROTATIONS = "Rotations";

  private static final String HOW_TO_READ =
      "A rotation is a hero who was away from his team's group for at least %d s and got to a lane"
          + " that is not his own, where a fight, a kill or his death came of it, or the lane he"
          + " left lost the creep count. The group is the biggest bunch of the team's living"
          + " heroes within %d units of each other; farming the jungle is not away. The creep"
          + " count compares what his lane partners and the enemy heroes of that lane killed in"
          + " the %d s after he left, which says little once the laning stage is over. \"trip\""
          + " says where he left, where he got and how it ended; \"what came of it\" counts kills"
          + " (or helped kills), creeps and fights in the trip and the few seconds after it."
          + " Verdict: good (a kill or a won fight came out of it), wasted (neither, and his lane"
          + " lost the creep count), died (he died before the trip ended), neutral (anything"
          + " else). Times are the game clock (m:ss). Write OK or REJECT in the owner verdict"
          + " column.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<TeamRotationDomain> rotations,
      List<FightDomain> fights,
      BehaviourProperties properties) {}

  private BehaviourLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(
        ReviewLoopReport.introduction(
            HOW_TO_READ.formatted(
                d.properties().getAwaySeconds(),
                d.properties().getClusterDistance(),
                d.properties().getProxyWindowSeconds()),
            summary(d)));
    out.add("");
    out.addAll(table(d));
    return String.join("\n", out) + "\n";
  }

  private static List<TeamRotationDomain> sorted(MatchData d) {
    return d.rotations().stream()
        .sorted(
            Comparator.comparing(TeamRotationDomain::getStartSeconds)
                .thenComparing(TeamRotationDomain::getPlayerSlot))
        .toList();
  }

  private static String summary(MatchData d) {
    if (d.rotations().isEmpty()) {
      return "no rotations stored (the analysis did not run, or nothing came of any trip).";
    }
    Map<String, Long> byVerdict =
        d.rotations().stream()
            .collect(Collectors.groupingBy(TeamRotationDomain::getVerdict, Collectors.counting()));
    String verdicts =
        List.of("GOOD", "WASTED", "NEUTRAL", "DIED").stream()
            .map(v -> "%d %s".formatted(byVerdict.getOrDefault(v, 0L), LoopWording.words(v)))
            .collect(Collectors.joining(", "));
    Map<String, Long> byTeam = teams(d);
    return "rotations %d (Radiant %d, Dire %d): %s; %d arrived by teleport."
        .formatted(
            d.rotations().size(),
            byTeam.getOrDefault("RADIANT", 0L),
            byTeam.getOrDefault("DIRE", 0L),
            verdicts,
            d.rotations().stream().filter(TeamRotationDomain::isTeleported).count());
  }

  private static Map<String, Long> teams(MatchData d) {
    return d.rotations().stream()
        .collect(Collectors.groupingBy(TeamRotationDomain::getTeam, Collectors.counting()));
  }

  private static List<String> table(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + ROTATIONS,
                "",
                "| rotation | trip | what came of it | the lane he left | verdict | owner verdict"
                    + " |",
                "|---|---|---|---|---|---|"));
    for (TeamRotationDomain r : sorted(d)) {
      out.add(
          "| %s | %s | %s | %s | %s | |"
              .formatted(
                  ReviewLoopReport.tableCell(label(d, r)),
                  ReviewLoopReport.tableCell(trip(r)),
                  ReviewLoopReport.tableCell(outcome(d, r)),
                  ReviewLoopReport.tableCell(laneLeft(r)),
                  LoopWording.words(r.getVerdict())));
    }
    return out;
  }

  private static String label(MatchData d, TeamRotationDomain r) {
    return "%s %s at %s"
        .formatted(
            LoopWording.team(r.getTeam()),
            LoopWording.hero(d.heroNames(), r.getPlayerSlot()),
            LoopWording.clock(r.getStartSeconds()));
  }

  private static String trip(TeamRotationDomain r) {
    AwayTrip.End end = Enums.find(AwayTrip.End.class, r.getEndReason());
    return "left %s (the group was in %s), got to %s on %s at %s, away %d s%s; %s"
        .formatted(
            LoopWording.place(r.getFromZone(), r.getFromLane()),
            LoopWording.place(r.getGroupZone(), r.getGroupLane()),
            LoopWording.place(r.getToZone(), r.getToLane()),
            "OWN".equals(r.getToSide()) ? "his own half" : "the enemy half",
            LoopWording.clock(r.getEndSeconds()),
            r.getEndSeconds() - r.getStartSeconds(),
            r.isTeleported() ? " (teleported)" : "",
            end == null ? "?" : end.words());
  }

  private static String outcome(MatchData d, TeamRotationDomain r) {
    List<String> parts = new ArrayList<>();
    if (r.getFights() > 0) {
      parts.add(
          "%d fight%s, %d won%s"
              .formatted(
                  r.getFights(),
                  r.getFights() == 1 ? "" : "s",
                  r.getFightsWon(),
                  fightWhere(d, r)));
    }
    if (r.getKills() > 0) {
      parts.add(
          "%d kill%s or assist%s"
              .formatted(r.getKills(), plural(r.getKills()), plural(r.getKills())));
    }
    if (r.getCreepKills() > 0) {
      parts.add("%d creeps".formatted(r.getCreepKills()));
    }
    return parts.isEmpty() ? "nothing" : String.join(", ", parts);
  }

  private static String plural(int n) {
    return n == 1 ? "" : "s";
  }

  private static String fightWhere(MatchData d, TeamRotationDomain r) {
    return d.fights().stream()
        .filter(f -> Objects.equals(f.getFightIndex(), r.getFightIndex()))
        .findFirst()
        .map(
            f ->
                " (at %s in %s)"
                    .formatted(
                        LoopWording.clock(f.getStartSeconds()),
                        LoopWording.place(f.getZone(), f.getLane())))
        .orElse("");
  }

  private static String laneLeft(TeamRotationDomain r) {
    if (r.getLaneLeft() == null) {
      return "not laning then (no lane, or the laning was over)";
    }
    if (r.getProxyOwnKills() == null) {
      return "%s lane, no creep count".formatted(LoopWording.words(r.getLaneLeft()));
    }
    return "%s lane: his partners %d creeps, the enemy %d"
        .formatted(
            LoopWording.words(r.getLaneLeft()), r.getProxyOwnKills(), r.getProxyEnemyKills());
  }
}
