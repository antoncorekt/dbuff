package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.Posture;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The group-or-split and lane-swap tables of the behaviour report; they only lay out what {@code
 * team_split_stretch} and {@code team_lane_swap} hold. The owner's verdict columns are left empty.
 */
final class BehaviourSplitSections {
  static final String STRETCHES = "Group or split";
  static final String SWAPS = "Lane swaps";

  private static final String STRETCHES_HOW_TO_READ =
      "Group or split: from the end of the last lane pair (or %s when no lane result exists), each"
          + " minute of a team is labelled together (%d or more of its living heroes within %d"
          + " units of each other, or all of them when fewer are alive), split up (not together,"
          + " and %d or more living heroes each alone: nobody of his team within %d units, not"
          + " farming the jungle) or mixed (anything else). Dead heroes are not counted. The table"
          + " shows stretches of at least %d minutes, not single minutes; a shorter one is joined"
          + " to its longer neighbour. \"what happened\" counts, inside the stretch, the towers the"
          + " team lost and took, its heroes that died and the enemy heroes that died. Times are"
          + " the game clock (m:ss). Write OK or REJECT in the owner verdict column.";

  private static final String SWAPS_HOW_TO_READ =
      "Lane swap: a hero whose stored lane differs from the lane he stood in for most of minutes"
          + " %d to %d (at least %d%% of his living time there). A hero who spent that time in the"
          + " jungle, the river or the base, or who was split between lanes, has no main lane and"
          + " is not listed. His role is in brackets when it is known. Times are the game clock"
          + " (m:ss). Write OK or REJECT in the owner verdict column.";

  private BehaviourSplitSections() {}

  static String stretchesHowToRead(BehaviourProperties p) {
    return STRETCHES_HOW_TO_READ.formatted(
        LoopWording.clock(p.getSplitFallbackStartSeconds()),
        p.getGroupedMinHeroes(),
        p.getClusterDistance(),
        p.getSplitMinAloneHeroes(),
        p.getClusterDistance(),
        p.getMinStretchMinutes());
  }

  static String swapsHowToRead(BehaviourProperties p) {
    return SWAPS_HOW_TO_READ.formatted(
        p.getSwapFromMinute(), p.getSwapToMinute(), Math.round(p.getSwapMinShare() * 100));
  }

  static String summary(List<TeamSplitStretchDomain> stretches, List<TeamLaneSwapDomain> swaps) {
    String postures =
        stretches.isEmpty()
            ? "no group-or-split stretches stored"
            : "%d stretches (%s)"
                .formatted(
                    stretches.size(),
                    Arrays.stream(Posture.values())
                        .map(
                            p ->
                                "%d %s"
                                    .formatted(
                                        stretches.stream()
                                            .filter(s -> p.name().equals(s.getLabel()))
                                            .count(),
                                        p.words()))
                        .collect(Collectors.joining(", ")));
    return "%s; %s."
        .formatted(
            postures,
            swaps.isEmpty()
                ? "no lane swaps"
                : "%d lane swap%s".formatted(swaps.size(), swaps.size() == 1 ? "" : "s"));
  }

  static List<String> stretchTable(List<TeamSplitStretchDomain> stretches) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + STRETCHES,
                "",
                "| stretch | how the team stood | what happened | owner verdict |",
                "|---|---|---|---|"));
    stretches.stream()
        .sorted(
            Comparator.comparing(TeamSplitStretchDomain::getStartSeconds)
                .thenComparing(TeamSplitStretchDomain::getTeam))
        .forEach(
            s ->
                out.add(
                    "| %s %s to %s | %s | %s | |"
                        .formatted(
                            LoopWording.team(s.getTeam()),
                            LoopWording.clock(s.getStartSeconds()),
                            LoopWording.clock(s.getEndSeconds()),
                            posture(s),
                            ReviewLoopReport.tableCell(happened(s)))));
    return out;
  }

  static List<String> swapTable(
      List<TeamLaneSwapDomain> swaps, Map<Long, String> heroNames, Map<Long, Integer> roles) {
    List<String> out =
        new ArrayList<>(
            List.of("## " + SWAPS, "", "| swap | what changed | owner verdict |", "|---|---|---|"));
    swaps.stream()
        .sorted(
            Comparator.comparing(TeamLaneSwapDomain::getFromSeconds)
                .thenComparing(TeamLaneSwapDomain::getPlayerSlot))
        .forEach(
            s ->
                out.add(
                    "| %s %s at %s | %s | |"
                        .formatted(
                            LoopWording.team(s.getTeam()),
                            LoopWording.hero(heroNames, s.getPlayerSlot()),
                            LoopWording.clock(s.getFromSeconds()),
                            ReviewLoopReport.tableCell(change(s, heroNames, roles)))));
    return out;
  }

  private static String posture(TeamSplitStretchDomain s) {
    Posture posture = Enums.find(Posture.class, s.getLabel());
    return posture == null ? "?" : posture.words();
  }

  private static String happened(TeamSplitStretchDomain s) {
    List<String> parts = new ArrayList<>();
    add(parts, s.getTowersLost(), "lost %d %s", "tower", "towers");
    add(parts, s.getHeroesLost(), "%d of its %s died", "heroes", "heroes");
    add(parts, s.getTowersTaken(), "took %d %s", "tower", "towers");
    add(parts, s.getKills(), "%d %s died", "enemy hero", "enemy heroes");
    return parts.isEmpty() ? "nothing" : String.join(", ", parts);
  }

  private static void add(
      List<String> parts, Integer count, String format, String one, String many) {
    if (count != null && count > 0) {
      parts.add(format.formatted(count, count == 1 ? one : many));
    }
  }

  private static String change(
      TeamLaneSwapDomain s, Map<Long, String> heroNames, Map<Long, Integer> roles) {
    Integer role = roles.get(s.getPlayerSlot());
    return "%s moved %s%s from the %s to the %s at %s (in it until %s)"
        .formatted(
            LoopWording.team(s.getTeam()),
            LoopWording.hero(heroNames, s.getPlayerSlot()),
            role == null ? "" : " (" + LoopWording.role(role) + ")",
            LoopWording.place("LANE", s.getLaneBefore()),
            LoopWording.place("LANE", s.getLaneAfter()),
            LoopWording.clock(s.getFromSeconds()),
            LoopWording.clock(s.getToSeconds()));
  }
}
