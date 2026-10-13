package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what the tables hold; the
 * three tables headed {@link #FIGHTS}, {@link #REASONS} and {@link #NOTES} are what {@link
 * FightLoopTopic} lets the loop compare between rounds. The verdict columns are left empty for the
 * reviewers, and the stored codes and evidence behind a reason are in the {@link #NOTES} table
 * only.
 */
final class FightLoopReport {
  static final String FIGHTS = "Lost fights";
  static final String REASONS = "Reasons";
  static final String NOTES = "Notes behind the reasons";
  static final String NO_SWING = "none";
  static final String NOT_RANKED = "not ranked (early fight)";

  /** How far OpenDota's teamfight may start before, or end after, the replay's fight. */
  private static final int OPEN_DOTA_SLACK_SECONDS = 10;

  private static final String HOW_TO_READ =
      "each row of \"Lost fights\" is a teamfight one side lost, with the likeliest reason first;"
          + " \"Reasons\" lists every reason stored for those fights in order of rank. Times are"
          + " the game clock (m:ss) and gold is what the loser's deaths cost. Write OK or REJECT"
          + " in the assistant and owner columns of \"Reasons\".";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      MatchExplanation explanation,
      Map<Long, String> heroNames,
      List<TeamfightDomain> openDotaFights,
      List<TeamfightPlayerDomain> openDotaPlayers) {}

  private FightLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    List<Row> rows = rows(d);
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(rows)));
    out.add("");
    if (rows.isEmpty()) {
      out.add("**No lost fights stored (no fights with a winner, or the analysis did not run).**");
      out.add("");
    }
    out.addAll(fightsTable(d, rows));
    out.add("");
    out.addAll(reasonsTable(d, rows));
    out.add("");
    out.addAll(notes(d, rows));
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
      s.fights().forEach(f -> rows.add(new Row("swing " + s.swing().getSpikeIndex(), f)));
    }
    d.explanation().otherFights().forEach(f -> rows.add(new Row(swingOf(f), f)));
    rows.sort(Comparator.comparing((Row r) -> r.lost().fight().getFightIndex()));
    return rows;
  }

  private static String swingOf(LostFight lost) {
    Integer spike = lost.explanation().getSpikeIndex();
    return spike == null ? NO_SWING : "swing " + spike + ", but the side it favoured lost this";
  }

  private static String summary(List<Row> rows) {
    if (rows.isEmpty()) {
      return "no lost fights stored.";
    }
    Map<String, Long> byLoser =
        rows.stream()
            .collect(Collectors.groupingBy(r -> loserName(r.lost()), Collectors.counting()));
    long unexplained = rows.stream().filter(r -> r.lost().reasons().isEmpty()).count();
    Row costliest =
        rows.stream()
            .filter(r -> r.lost().explanation().getGoldLost() != null)
            .max(Comparator.comparingLong(r -> r.lost().explanation().getGoldLost()))
            .orElse(null);
    return "%d lost %s (%s); %s%s."
        .formatted(
            rows.size(),
            rows.size() == 1 ? "fight" : "fights",
            byLoser.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " lost " + e.getValue())
                .collect(Collectors.joining(", ")),
            unexplained == 0 ? "every one has a reason" : unexplained + " have no reason stored",
            costliest == null
                ? ""
                : "; the costliest was fight %d at %s, %s lost %s"
                    .formatted(
                        costliest.lost().fight().getFightIndex(),
                        LoopWording.clock(costliest.lost().fight().getStartSeconds()),
                        loserName(costliest.lost()),
                        LoopWording.gold(costliest.lost().explanation().getGoldLost())));
  }

  private static String loserName(LostFight lost) {
    return LoopWording.team(lost.explanation().getLosingTeam());
  }

  private static String fightLabel(LostFight lost) {
    FightDomain f = lost.fight();
    return "%s lost fight %d at %s"
        .formatted(loserName(lost), f.getFightIndex(), LoopWording.clock(f.getStartSeconds()));
  }

  private static List<String> fightsTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + FIGHTS,
                "",
                "| fight | main reason | gold lost | swing | OpenDota teamfight |",
                "|---|---|---|---|---|"));
    for (Row r : rows) {
      LostFight lost = r.lost();
      TeamSide loser = Enums.find(TeamSide.class, lost.explanation().getLosingTeam());
      Integer unmatched = lost.explanation().getUnmatchedDeaths();
      String gold = LoopWording.gold(lost.explanation().getGoldLost());
      out.add(
          "| %s | %s | %s | %s | %s |"
              .formatted(
                  ReviewLoopReport.tableCell(fightLabel(lost)),
                  ReviewLoopReport.tableCell(mainReason(d, lost)),
                  unpriced(gold, unmatched),
                  ReviewLoopReport.tableCell(r.swing()),
                  openDotaFight(d, lost.fight())
                      .map(t -> openDotaText(d, t, loser))
                      .orElse("none")));
    }
    return out;
  }

  private static String unpriced(String gold, Integer unmatched) {
    if (unmatched == null) {
      return "?".equals(gold) ? gold : gold + " (deaths not priced: ?)";
    }
    return unmatched == 0 ? gold : "%s (%d deaths not priced)".formatted(gold, unmatched);
  }

  /** The sentence of each reason of a fight; a repeated sentence gets " (2)", " (3)". */
  private static List<String> sentences(MatchData d, LostFight lost) {
    Map<String, Integer> seen = new HashMap<>();
    List<String> out = new ArrayList<>();
    for (RankedReason ranked : lost.reasons()) {
      String text = reasonText(d, ranked);
      int n = seen.merge(text, 1, Integer::sum);
      out.add(n == 1 ? text : text + " (" + n + ")");
    }
    return out;
  }

  private static String mainReason(MatchData d, LostFight lost) {
    if (lost.reasons().isEmpty()) {
      return "no reason found";
    }
    String first = reasonText(d, lost.reasons().get(0));
    int more = lost.totalReasons() - 1;
    return more > 0 ? first + " (and " + more + " more)" : first;
  }

  private static String openDotaText(MatchData d, TeamfightDomain t, TeamSide loser) {
    return "%s deaths, %s gold change for the loser"
        .formatted(t.getDeaths() == null ? "?" : t.getDeaths(), goldDelta(d, t, loser));
  }

  private static List<String> reasonsTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + REASONS,
                "",
                "| reason | fight | rank | also | severity | assistant | owner |",
                "|---|---|---|---|---|---|---|"));
    for (Row r : rows) {
      List<String> sentences = sentences(d, r.lost());
      for (int i = 0; i < sentences.size(); i++) {
        RankedReason ranked = r.lost().reasons().get(i);
        out.add(
            "| %s | %s | %s | %s | %s | | |"
                .formatted(
                    ReviewLoopReport.tableCell(sentences.get(i)),
                    fightLabel(r.lost()),
                    ranked.ranked() ? String.valueOf(ranked.rank()) : NOT_RANKED,
                    ReviewLoopReport.tableCell(also(d, ranked)),
                    severity(ranked)));
      }
    }
    return out;
  }

  private static String severity(RankedReason ranked) {
    Double severity = ranked.reason().getSeverity();
    return severity == null ? "?" : String.format(Locale.ROOT, "%.2f", severity);
  }

  private static String reasonText(MatchData d, RankedReason ranked) {
    var reason = ranked.reason();
    return LoopWording.reason(reason, LoopWording.hero(d.heroNames(), reason.getSubjectSlot()));
  }

  private static String also(MatchData d, RankedReason ranked) {
    String hero = LoopWording.hero(d.heroNames(), ranked.reason().getSubjectSlot());
    return ranked.also().stream()
        .map(code -> LoopWording.reasonWithoutHero(code, hero))
        .collect(Collectors.joining(", "));
  }

  /** The stored code and evidence of every reason, for whoever needs to trace one. */
  private static List<String> notes(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + NOTES,
                "",
                "| fight | reason | stored code | evidence |",
                "|---|---|---|---|"));
    for (Row r : rows) {
      List<String> sentences = sentences(d, r.lost());
      for (int i = 0; i < sentences.size(); i++) {
        var reason = r.lost().reasons().get(i).reason();
        out.add(
            "| %s | %s | %s | %s |"
                .formatted(
                    fightLabel(r.lost()),
                    ReviewLoopReport.tableCell(sentences.get(i)),
                    ReviewLoopReport.tableCell(orQuestion(reason.getCode())),
                    ReviewLoopReport.tableCell(orQuestion(reason.getEvidence()))));
      }
    }
    return out;
  }

  private static String orQuestion(String value) {
    return value == null || value.isBlank() ? "?" : value;
  }

  private static String swingLine(SwingExplanation s) {
    GameSpikeDomain g = s.swing();
    return "### Swing %d, %s to %s: %s gained %s gold (%s%%), cause: %s%n%s"
        .formatted(
            g.getSpikeIndex(),
            LoopWording.clock(g.getStartSeconds()),
            LoopWording.clock(g.getEndSeconds()),
            LoopWording.team(g.getBenefitingTeam()),
            LoopWording.gold(g.getDeltaGold()),
            g.getDeltaPercent(),
            LoopWording.words(g.getPrimaryCause()),
            s.fights().isEmpty()
                ? "no lost fight of the losing side stored"
                : "lost fights: "
                    + s.fights().stream()
                        .map(f -> String.valueOf(f.fight().getFightIndex()))
                        .collect(Collectors.joining(", ")));
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
        : LoopWording.gold(deltas.stream().mapToLong(Long::longValue).sum());
  }
}
