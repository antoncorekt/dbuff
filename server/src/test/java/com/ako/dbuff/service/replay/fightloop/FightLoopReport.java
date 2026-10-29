package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TeamfightDomain;
import com.ako.dbuff.dao.model.TeamfightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.MatchExplanation;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.SwingExplanation;
import com.ako.dbuff.service.replay.analysis.importance.AdvantageSeries;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what the tables hold; the
 * three tables headed {@link #FIGHTS}, {@link #REASONS} and {@link #NOTES} are what {@link
 * FightLoopTopic} lets the loop compare between rounds. The verdict columns are left empty for the
 * reviewers, and the stored codes and evidence behind a reason are in the {@link #NOTES} table
 * only.
 */
final class FightLoopReport {
  static final String GRAPH = "Advantage graph";
  static final String FIGHTS = "Lost fights";
  static final String REASONS = "Reasons";
  static final String NOTES = "Notes behind the reasons";
  static final String NO_SWING = "none";
  static final String NOT_RANKED = "not ranked (early fight)";

  /** How far OpenDota's teamfight may start before, or end after, the replay's fight. */
  private static final int OPEN_DOTA_SLACK_SECONDS = 10;

  private static final String HOW_TO_READ =
      "\"Advantage graph\" is the gold and experience lead of each team minute by minute, with the"
          + " swings, lead changes and important fights marked. \"Lost fights\" lists every"
          + " fight with a winner and whether it was important (it moved the winner's gold lead"
          + " enough, or flipped the lead to the winner); \"Reasons\" lists the reasons of the"
          + " important fights in order of rank. Times are the game clock (m:ss), \"gold change\""
          + " is the winner's gain in lead from the start of the fight to a minute after it, and"
          + " \"gold lost\" is what the loser's deaths cost. Write OK or REJECT in the assistant"
          + " and owner columns of \"Reasons\".";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      MatchExplanation explanation,
      Map<Long, String> heroNames,
      AdvantageSeries series,
      List<TeamfightDomain> openDotaFights,
      List<TeamfightPlayerDomain> openDotaPlayers) {}

  private FightLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    List<Row> rows = rows(d);
    List<Row> important = rows.stream().filter(r -> r.lost().important()).toList();
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(rows, important)));
    out.add("");
    out.addAll(graphTable(d, rows));
    out.add("");
    if (rows.isEmpty()) {
      out.add("**No lost fights stored (no fights with a winner, or the analysis did not run).**");
      out.add("");
    }
    out.addAll(fightsTable(d, rows));
    out.add("");
    out.addAll(reasonsTable(d, important));
    out.add("");
    out.addAll(notes(d, important));
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

  private static String summary(List<Row> rows, List<Row> important) {
    if (rows.isEmpty()) {
      return "no lost fights stored.";
    }
    boolean known = rows.stream().anyMatch(r -> r.lost().importance() != null);
    Map<String, Long> byLoser =
        important.stream()
            .collect(Collectors.groupingBy(r -> loserName(r.lost()), Collectors.counting()));
    long unexplained = important.stream().filter(r -> r.lost().reasons().isEmpty()).count();
    Row costliest =
        important.stream()
            .filter(r -> r.lost().explanation().getGoldLost() != null)
            .max(Comparator.comparingLong(r -> r.lost().explanation().getGoldLost()))
            .orElse(null);
    return "%s%s%s%s."
        .formatted(
            known
                ? "%d of %d decided fights are important".formatted(important.size(), rows.size())
                : "%d decided %s, importance not known, no advantage series"
                    .formatted(rows.size(), rows.size() == 1 ? "fight" : "fights"),
            byLoser.isEmpty()
                ? ""
                : " ("
                    + byLoser.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + " lost " + e.getValue())
                        .collect(Collectors.joining(", "))
                    + ")",
            important.isEmpty()
                ? ""
                : "; "
                    + (unexplained == 0
                        ? "every one has a reason"
                        : unexplained + " have no reason stored"),
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

  private static List<String> graphTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + GRAPH,
                "",
                "| minute | gold advantage | XP advantage | what happened |",
                "|---|---|---|---|"));
    AdvantageSeries series = d.series();
    if (series == null) {
      return List.of("## " + GRAPH, "", "**No advantage series stored for this match.**");
    }
    int minutes = (int) (series.endSeconds() / AdvantageSeries.MINUTE) + 1;
    for (int m = 0; m < minutes; m++) {
      double now = m * (double) AdvantageSeries.MINUTE;
      double before = Math.max(0, now - AdvantageSeries.MINUTE);
      List<String> marks = new ArrayList<>();
      leadChange("gold", series.radiantGold()[Math.max(0, m - 1)], series.radiantGold()[m], marks);
      leadChange("XP", series.radiantXp()[Math.max(0, m - 1)], series.radiantXp()[m], marks);
      for (SwingExplanation s : d.explanation().swings()) {
        GameSpikeDomain g = s.swing();
        if (g.getStartSeconds() / AdvantageSeries.MINUTE == m) {
          marks.add(
              "swing %d starts: %s gains %s"
                  .formatted(
                      g.getSpikeIndex(),
                      LoopWording.team(g.getBenefitingTeam()),
                      LoopWording.gold(g.getDeltaGold())));
        }
        if (g.getEndSeconds() / AdvantageSeries.MINUTE == m) {
          marks.add(
              "swing %d ends%s"
                  .formatted(
                      g.getSpikeIndex(),
                      Boolean.TRUE.equals(g.getDirectionChange())
                          ? ", " + LoopWording.team(g.getBenefitingTeam()) + " took the lead"
                          : ""));
        }
      }
      for (Row r : rows) {
        FightImportanceDomain i = r.lost().importance();
        if (i != null
            && i.isImportant()
            && (int) (r.lost().fight().getStartSeconds() / AdvantageSeries.MINUTE) == m) {
          marks.add(
              "important fight %d at %s: %s lost, %s gold"
                  .formatted(
                      r.lost().fight().getFightIndex(),
                      LoopWording.clock(r.lost().fight().getStartSeconds()),
                      loserName(r.lost()),
                      LoopWording.change(i.getWinningTeam(), i.getGoldChange())));
        }
      }
      out.add(
          "| %d:00 | %s | %s | %s |"
              .formatted(
                  m,
                  LoopWording.advantage(series.radiantGold()[m]),
                  LoopWording.advantage(series.radiantXp()[m]),
                  ReviewLoopReport.tableCell(String.join("; ", marks))));
    }
    return out;
  }

  private static void leadChange(String what, double before, double after, List<String> marks) {
    if (before * after < 0) {
      marks.add("%s lead goes to %s".formatted(what, after > 0 ? "Radiant" : "Dire"));
    }
  }

  private static List<String> fightsTable(MatchData d, List<Row> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + FIGHTS,
                "",
                "| fight | important | gold change | XP change | share of net worth | flags | main"
                    + " reason | gold lost | swing | OpenDota teamfight |",
                "|---|---|---|---|---|---|---|---|---|---|"));
    for (Row r : rows) {
      LostFight lost = r.lost();
      TeamSide loser = Enums.find(TeamSide.class, lost.explanation().getLosingTeam());
      Integer unmatched = lost.explanation().getUnmatchedDeaths();
      String gold = LoopWording.gold(lost.explanation().getGoldLost());
      out.add(
          "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |"
              .formatted(
                  ReviewLoopReport.tableCell(fightLabel(lost)),
                  importantText(lost),
                  changeText(lost, FightImportanceDomain::getGoldChange),
                  changeText(lost, FightImportanceDomain::getXpChange),
                  shareText(lost),
                  flagsText(lost),
                  ReviewLoopReport.tableCell(
                      lost.important() ? mainReason(d, lost) : "not told (unimportant)"),
                  unpriced(gold, unmatched),
                  ReviewLoopReport.tableCell(r.swing()),
                  openDotaFight(d, lost.fight(), rows)
                      .map(t -> openDotaText(d, t, loser))
                      .orElse("none")));
    }
    return out;
  }

  private static String importantText(LostFight lost) {
    FightImportanceDomain i = lost.importance();
    return i == null ? "unknown" : i.isImportant() ? "yes" : "no";
  }

  private static String changeText(LostFight lost, Function<FightImportanceDomain, Long> value) {
    FightImportanceDomain i = lost.importance();
    return i == null || value.apply(i) == null
        ? "?"
        : LoopWording.change(i.getWinningTeam(), value.apply(i));
  }

  private static String shareText(LostFight lost) {
    FightImportanceDomain i = lost.importance();
    return i == null || i.getSharePercent() == null
        ? "?"
        : String.format(Locale.ROOT, "%.1f%%", i.getSharePercent());
  }

  private static String flagsText(LostFight lost) {
    FightImportanceDomain i = lost.importance();
    if (i == null) {
      return "?";
    }
    List<String> flags = new ArrayList<>();
    if (Boolean.TRUE.equals(i.getLeadFlip())) {
      flags.add(
          Boolean.TRUE.equals(i.getFlipToWinner())
              ? "lead flipped to the winner"
              : "lead flipped to the loser");
    }
    if (Boolean.TRUE.equals(i.getSlopeBreak())) {
      flags.add("direction change");
    }
    return flags.isEmpty() ? "none" : String.join(", ", flags);
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

  /** The OpenDota teamfight that the fight overlaps most, unless another fight overlaps it more. */
  private static Optional<TeamfightDomain> openDotaFight(
      MatchData d, FightDomain fight, List<Row> rows) {
    return d.openDotaFights().stream()
        .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
        .filter(t -> overlap(t, fight) > 0)
        .filter(
            t ->
                rows.stream()
                    .map(r -> r.lost().fight())
                    .filter(f -> f.getStartSeconds() != null && f.getEndSeconds() != null)
                    .min(closest(t))
                    .filter(f -> f == fight)
                    .isPresent())
        .max(
            Comparator.comparingDouble((TeamfightDomain t) -> overlap(t, fight))
                .thenComparing(t -> -Math.abs(t.getStartTime() - fight.getStartSeconds())));
  }

  private static Comparator<FightDomain> closest(TeamfightDomain t) {
    return Comparator.comparingDouble((FightDomain f) -> -overlap(t, f))
        .thenComparingDouble(f -> Math.abs(t.getStartTime() - f.getStartSeconds()))
        .thenComparing(FightDomain::getFightIndex, Comparator.nullsLast(Comparator.naturalOrder()));
  }

  private static double overlap(TeamfightDomain t, FightDomain f) {
    if (f.getStartSeconds() == null || f.getEndSeconds() == null) {
      return 0;
    }
    double from = f.getStartSeconds() - OPEN_DOTA_SLACK_SECONDS;
    double to = f.getEndSeconds() + OPEN_DOTA_SLACK_SECONDS;
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
