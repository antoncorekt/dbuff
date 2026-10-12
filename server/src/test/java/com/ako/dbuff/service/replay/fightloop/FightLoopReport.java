package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The review report of one stored match, as markdown. It only lays out what the tables hold; the
 * two tables headed {@link #FIGHTS} and {@link #REASONS} are what {@link FightLoopTopic} lets the
 * loop compare between rounds. The verdict columns are left empty for the reviewers.
 */
final class FightLoopReport {
  static final String FIGHTS = "Lost fights";
  static final String REASONS = "Reasons";
  static final String NO_SWING = "-";
  static final String NOT_RANKED = "not ranked (early fight)";

  /** How far OpenDota's teamfight may start before, or end after, the replay's fight. */
  private static final int OPEN_DOTA_SLACK_SECONDS = 10;

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      MatchExplanation explanation,
      List<ReplayPlayerDomain> replayPlayers,
      List<TeamfightDomain> openDotaFights,
      List<TeamfightPlayerDomain> openDotaPlayers) {}

  private FightLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    List<Row> rows = rows(d);
    if (rows.isEmpty()) {
      out.add("**No lost fights stored (no fights with a winner, or the analysis did not run).**");
      out.add("");
    }
    out.addAll(fightsTable(d, rows));
    out.add("");
    out.addAll(reasonsTable(d, rows));
    out.add("");
    out.add("## Swings");
    for (SwingExplanation swing : d.explanation().swings()) {
      out.add("");
      out.add(swingLine(swing));
    }
    return String.join("\n", out) + "\n";
  }

  private record Row(String swing, LostFight lost) {}

  private static List<Row> rows(MatchData d) {
    List<Row> rows = new ArrayList<>();
    for (SwingExplanation s : d.explanation().swings()) {
      s.fights().forEach(f -> rows.add(new Row(String.valueOf(s.swing().getSpikeIndex()), f)));
    }
    d.explanation().otherFights().forEach(f -> rows.add(new Row(swingOf(f), f)));
    rows.sort(Comparator.comparing((Row r) -> r.lost().fight().getFightIndex()));
    return rows;
  }

  private static String swingOf(LostFight lost) {
    Integer spike = lost.explanation().getSpikeIndex();
    return spike == null ? NO_SWING : spike + " (benefiting side lost)";
  }

  private static List<String> fightsTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + FIGHTS,
                "",
                "| swing | fight | time | loser | gold lost | unmatched deaths | reasons | OD"
                    + " teamfight | OD deaths | OD gold delta of the loser |",
                "|---|---|---|---|---|---|---|---|---|---|"));
    for (Row r : rows) {
      FightDomain f = r.lost().fight();
      TeamSide loser = Enums.find(TeamSide.class, r.lost().explanation().getLosingTeam());
      Optional<TeamfightDomain> od = openDotaFight(d, f);
      out.add(
          "| %s | %d | %s | %s | %s | %s | %d | %s | %s | %s |"
              .formatted(
                  ReviewLoopReport.tableCell(r.swing()),
                  f.getFightIndex(),
                  clock(f.getStartSeconds()),
                  orQuestion(loser),
                  orQuestion(r.lost().explanation().getGoldLost()),
                  orQuestion(r.lost().explanation().getUnmatchedDeaths()),
                  r.lost().totalReasons(),
                  od.map(t -> "#" + t.getFightIndex()).orElse("none"),
                  od.map(t -> String.valueOf(t.getDeaths())).orElse("none"),
                  od.map(t -> goldDelta(d, t, loser)).orElse("none")));
    }
    return out;
  }

  private static List<String> reasonsTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + REASONS,
                "",
                "| swing | fight | code | subject | rank | also | severity | evidence | assistant"
                    + " | owner |",
                "|---|---|---|---|---|---|---|---|---|---|"));
    for (Row r : rows) {
      for (RankedReason ranked : r.lost().reasons()) {
        var reason = ranked.reason();
        out.add(
            "| %s | %d | %s | %s | %s | %s | %s | %s | | |"
                .formatted(
                    ReviewLoopReport.tableCell(r.swing()),
                    r.lost().fight().getFightIndex(),
                    orQuestion(reason.getCode()),
                    ReviewLoopReport.tableCell(
                        subject(d, reason.getSubjectSlot(), reason.getTeam())),
                    ranked.ranked() ? String.valueOf(ranked.rank()) : NOT_RANKED,
                    ReviewLoopReport.tableCell(String.join(", ", ranked.also())),
                    reason.getSeverity() == null
                        ? "?"
                        : String.format(Locale.ROOT, "%.2f", reason.getSeverity()),
                    ReviewLoopReport.tableCell(
                        reason.getEvidence() == null ? "" : reason.getEvidence())));
      }
    }
    return out;
  }

  private static String swingLine(SwingExplanation s) {
    GameSpikeDomain g = s.swing();
    return "### Swing %d, %s to %s, %s gold (%s%%), %s, cause %s%n%s"
        .formatted(
            g.getSpikeIndex(),
            clock(g.getStartSeconds()),
            clock(g.getEndSeconds()),
            g.getDeltaGold(),
            g.getDeltaPercent(),
            g.getBenefitingTeam() + " benefits",
            g.getPrimaryCause(),
            s.fights().isEmpty()
                ? "no lost fight of the losing side stored"
                : "lost fights: "
                    + s.fights().stream()
                        .map(f -> String.valueOf(f.fight().getFightIndex()))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
  }

  private static String subject(MatchData d, Long slot, String team) {
    if (slot == null) {
      return team + " team";
    }
    String hero =
        d.replayPlayers().stream()
            .filter(p -> slot.equals(p.getPlayerSlot()) && p.getHeroEntityName() != null)
            .findFirst()
            .map(ReplayPlayerDomain::getHeroEntityName)
            .orElse("hero ?");
    return "slot %d (%s)".formatted(slot, hero);
  }

  /** The OpenDota teamfight that overlaps the fight most; of equal overlap, the nearest start. */
  private static Optional<TeamfightDomain> openDotaFight(MatchData d, FightDomain fight) {
    if (fight.getStartSeconds() == null || fight.getEndSeconds() == null) {
      return Optional.empty();
    }
    double from = fight.getStartSeconds() - OPEN_DOTA_SLACK_SECONDS;
    double to = fight.getEndSeconds() + OPEN_DOTA_SLACK_SECONDS;
    return d.openDotaFights().stream()
        .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
        .filter(t -> overlap(t, from, to) > 0)
        .max(
            Comparator.comparingDouble((TeamfightDomain t) -> overlap(t, from, to))
                .thenComparing(t -> -Math.abs(t.getStartTime() - fight.getStartSeconds())));
  }

  private static double overlap(TeamfightDomain t, double from, double to) {
    return Math.min(t.getEndTime(), to) - Math.max(t.getStartTime(), from);
  }

  private static String goldDelta(MatchData d, TeamfightDomain fight, TeamSide loser) {
    List<Long> deltas =
        d.openDotaPlayers().stream()
            .filter(
                p ->
                    fight.getFightIndex().equals(p.getFightIndex())
                        && p.getGoldDelta() != null
                        && p.getPlayerSlot() != null
                        && TeamSide.ofSlot(p.getPlayerSlot()) == loser)
            .map(TeamfightPlayerDomain::getGoldDelta)
            .toList();
    return deltas.isEmpty()
        ? "?"
        : String.valueOf(deltas.stream().mapToLong(Long::longValue).sum());
  }

  private static String clock(Number seconds) {
    if (seconds == null) {
      return "?";
    }
    long s = Math.round(seconds.doubleValue());
    return "%d:%02d".formatted(s / 60, s % 60);
  }

  private static String orQuestion(Object value) {
    return value == null ? "?" : String.valueOf(value);
  }
}
