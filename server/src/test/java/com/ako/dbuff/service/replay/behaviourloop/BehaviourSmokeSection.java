package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeOutcome;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeUse;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.BehaviourWording;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The smoke table of the behaviour report; it only lays out what {@code smoke_use} holds. The
 * owner's verdict column is left empty.
 */
final class BehaviourSmokeSection {
  static final String SMOKE = "Smoke";

  private static final String HOW_TO_READ =
      "Smoke: a hero used a Smoke of Deceit. The group is the heroes of his team that got his smoke"
          + " within %d s of the use. The smoke ends for the group when it breaks for the first of"
          + " them (a hero came near an enemy, or died), or when it runs out; when the log gives no"
          + " removal or duration, how it ended is not known. \"where\" is the zone of the group's"
          + " middle at the start and at the end. Only what the group did counts, from the use to"
          + " %d s after the smoke ended: paid off (a group member killed or helped kill an enemy"
          + " hero, was among the heroes who took an enemy tower or barracks, or killed Roshan),"
          + " backfired (nothing of that, and a hero of the group died), else came to nothing."
          + " Kills and towers of teammates outside the group do not count. A use with no smoke"
          + " logged for the user is not listed; two uses by one group within the group window are"
          + " one row. Times are the game clock (m:ss). Write OK or REJECT in the owner verdict"
          + " column.";

  private BehaviourSmokeSection() {}

  static String howToRead(BehaviourProperties p) {
    return HOW_TO_READ.formatted(p.getSmokeGroupSeconds(), p.getSmokeOutcomeSeconds());
  }

  static String summary(List<SmokeUseDomain> smokes) {
    if (smokes.isEmpty()) {
      return "no smoke uses stored";
    }
    Map<String, Long> byTeam =
        smokes.stream()
            .collect(Collectors.groupingBy(SmokeUseDomain::getTeam, Collectors.counting()));
    String outcomes =
        Arrays.stream(SmokeOutcome.values())
            .map(
                o ->
                    "%d %s"
                        .formatted(count(smokes, SmokeUseDomain::getOutcome, o.name()), o.words()))
            .collect(Collectors.joining(", "));
    return "%d %s (Radiant %d, Dire %d): %s; %d ran out, %d broke, %d not known"
        .formatted(
            smokes.size(),
            smokes.size() == 1 ? "use" : "uses",
            byTeam.getOrDefault("RADIANT", 0L),
            byTeam.getOrDefault("DIRE", 0L),
            outcomes,
            count(smokes, SmokeUseDomain::getEndReason, SmokeUse.End.EXPIRED.name()),
            count(smokes, SmokeUseDomain::getEndReason, SmokeUse.End.BROKEN.name()),
            count(smokes, SmokeUseDomain::getEndReason, SmokeUse.End.UNKNOWN.name()));
  }

  static List<String> table(List<SmokeUseDomain> smokes, Map<Long, String> heroNames) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + SMOKE,
                "",
                "| smoke | in the group | from to | where it went | outcome | what happened | owner"
                    + " verdict |",
                "|---|---|---|---|---|---|---|"));
    smokes.stream()
        .sorted(
            Comparator.comparing(SmokeUseDomain::getStartSeconds)
                .thenComparing(SmokeUseDomain::getUserSlot))
        .forEach(
            s ->
                out.add(
                    "| %s %s at %s | %s | %s | %s | %s | %s | |"
                        .formatted(
                            LoopWording.team(s.getTeam()),
                            LoopWording.hero(heroNames, s.getUserSlot()),
                            LoopWording.clock(s.getStartSeconds()),
                            ReviewLoopReport.tableCell(BehaviourWording.group(s, heroNames)),
                            ReviewLoopReport.tableCell(span(s)),
                            ReviewLoopReport.tableCell(BehaviourWording.smokeWhere(s)),
                            outcome(s),
                            ReviewLoopReport.tableCell(happened(s, heroNames)))));
    return out;
  }

  private static long count(
      List<SmokeUseDomain> smokes, Function<SmokeUseDomain, String> column, String value) {
    return smokes.stream().filter(s -> value.equals(column.apply(s))).count();
  }

  private static String span(SmokeUseDomain s) {
    SmokeUse.End end = Enums.find(SmokeUse.End.class, s.getEndReason());
    return "%s to %s (%d s), the smoke %s"
        .formatted(
            LoopWording.clock(s.getStartSeconds()),
            LoopWording.clock(s.getEndSeconds()),
            s.getEndSeconds() - s.getStartSeconds(),
            end == null ? "?" : end.words());
  }

  private static String outcome(SmokeUseDomain s) {
    SmokeOutcome outcome = Enums.find(SmokeOutcome.class, s.getOutcome());
    return outcome == null ? "?" : outcome.words();
  }

  private static String happened(SmokeUseDomain s, Map<Long, String> heroNames) {
    List<String> parts = new ArrayList<>();
    add(parts, s.getKills(), "killed or helped kill %d enemy %s", "hero", "heroes");
    add(parts, s.getBuildings(), "%d enemy %s fell", "tower or barracks", "towers or barracks");
    if (s.getRoshanKills() != null && s.getRoshanKills() > 0) {
      parts.add("the team killed Roshan");
    }
    add(parts, s.getGroupDeaths(), "%d of the group died", "", "");
    if (s.getEnemyDeaths() != null && s.getEnemyDeaths() > 0 && !isKill(s)) {
      parts.add(
          "%d enemy %s died to others"
              .formatted(s.getEnemyDeaths(), s.getEnemyDeaths() == 1 ? "hero" : "heroes"));
    }
    return parts.isEmpty() ? "nothing happened in that time" : String.join(", ", parts);
  }

  private static boolean isKill(SmokeUseDomain s) {
    return Objects.requireNonNullElse(s.getKills(), 0) > 0;
  }

  private static void add(
      List<String> parts, Integer count, String format, String one, String many) {
    if (count != null && count > 0) {
      parts.add(format.formatted(count, count == 1 ? one : many));
    }
  }
}
