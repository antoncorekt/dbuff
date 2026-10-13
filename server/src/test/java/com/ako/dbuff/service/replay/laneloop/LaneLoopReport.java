package com.ako.dbuff.service.replay.laneloop;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LanePlayerStatDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.analysis.lane.LaneEnd;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what the lane tables hold;
 * the three tables headed {@link #LANES}, {@link #PLAYERS} and {@link #WHY} are what {@link
 * LaneLoopTopic} lets the loop compare between rounds. The verdict columns are left empty for the
 * reviewers.
 */
final class LaneLoopReport {
  static final String LANES = "Lanes";
  static final String PLAYERS = "Players in the lane";
  static final String WHY = "Why";

  private static final double CLEAR = 0.3;
  private static final double BIG = 0.6;

  private static final String HOW_TO_READ =
      "Each hero's lane is the lane he spent most time in during the first 4 minutes (a hero with"
          + " no lane of his own gets the lane of his teammates where he spent most of that lane's"
          + " window); the two teams' heroes in the same lane are one lane (Radiant's safe lane is"
          + " Dire's offlane). The laning stage of a hero starts when he first stands in his lane"
          + " and ends when he leaves it for the jungle or another lane (30 s), is away for 60 s,"
          + " or when the lane's first tower falls, at the latest at 15:00. \"Lanes\" says who won"
          + " each lane (creep score, XP, level, kills, denies and the tower; the score weighs"
          + " every creep by its gold and XP, so a siege creep counts more, and a deny by the XP it"
          + " takes) and how clearly. \"Players in the lane\" has each hero's last hits, denies and"
          + " score inside his own laning window and his share of his side's score. \"Why\" lists"
          + " the reasons found for each side, strongest first. Times are the game clock (m:ss)."
          + " Write OK or REJECT in the assistant and owner columns.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<LaneAssignmentDomain> assignments,
      List<LanePlayerStatDomain> stats,
      List<LaneResultDomain> results,
      List<LaneReasonDomain> reasons,
      int ultimateLevel) {}

  private LaneLoopReport() {}

  static String render(MatchData d) {
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d)));
    out.add("");
    out.addAll(lanesTable(d));
    out.add("");
    out.addAll(playersTable(d));
    out.add("");
    out.addAll(whyTable(d));
    return String.join("\n", out) + "\n";
  }

  private static String summary(MatchData d) {
    if (d.results().isEmpty()) {
      return "no lanes stored (the analysis did not run, or the match has no activity).";
    }
    List<Integer> pairs = pairIds(d);
    String lanes = pairs.stream().map(id -> laneSummary(d, id)).collect(Collectors.joining("; "));
    long without = d.assignments().stream().filter(a -> a.getLane() == null).count();
    return "%d lanes: %s; %d heroes had no lane; %d reasons stored."
        .formatted(pairs.size(), lanes, without, d.reasons().size());
  }

  private static String laneSummary(MatchData d, int pairId) {
    List<LaneResultDomain> sides = sides(d, pairId);
    LaneResultDomain first = sides.get(0);
    return "%s lane %s, %s"
        .formatted(
            words(first.getLane()), LoopWording.clock(first.getEndSeconds()), verdict(sides));
  }

  private static List<Integer> pairIds(MatchData d) {
    return d.results().stream().map(LaneResultDomain::getPairId).distinct().sorted().toList();
  }

  private static List<LaneResultDomain> sides(MatchData d, int pairId) {
    return d.results().stream()
        .filter(r -> r.getPairId() == pairId)
        .sorted(Comparator.comparing(r -> TeamSide.valueOf(r.getTeam())))
        .toList();
  }

  private static String words(String stored) {
    return LoopWording.words(stored);
  }

  private static String verdict(List<LaneResultDomain> sides) {
    LaneResultDomain winner =
        sides.stream().filter(r -> "WON".equals(r.getResult())).findFirst().orElse(null);
    if (winner != null) {
      return "%s won %s"
          .formatted(LoopWording.team(winner.getTeam()), strength(winner.getMargin()));
    }
    LaneResultDomain first = sides.get(0);
    if ("UNCONTESTED".equals(first.getResult())) {
      return "%s was alone".formatted(LoopWording.team(first.getTeam()));
    }
    return "even";
  }

  private static String strength(double margin) {
    double abs = Math.abs(margin);
    String how = abs >= BIG ? "by a lot" : abs >= CLEAR ? "clearly" : "narrowly";
    return "%s (%s)".formatted(how, signed(margin));
  }

  private static String signed(double margin) {
    return String.format(Locale.ROOT, "%+.2f", margin);
  }

  private static String laneLabel(MatchData d, int pairId) {
    return sides(d, pairId).stream()
        .map(
            r ->
                "%s %s lane: %s"
                    .formatted(
                        LoopWording.team(r.getTeam()),
                        positionWords(r.getPosition()),
                        heroes(d, pairId, r.getTeam())))
        .collect(Collectors.joining(" vs "));
  }

  private static String positionWords(String position) {
    return "MID".equals(position) ? "mid" : "SAFE".equals(position) ? "safe" : "off";
  }

  private static String heroes(MatchData d, int pairId, String team) {
    return d.assignments().stream()
        .filter(a -> Objects.equals(a.getPairId(), pairId) && team.equals(a.getTeam()))
        .sorted(Comparator.comparing(LaneAssignmentDomain::getPlayerSlot))
        .map(a -> LoopWording.hero(d.heroNames(), a.getPlayerSlot()))
        .collect(Collectors.collectingAndThen(Collectors.toList(), LaneLoopReport::joinNames));
  }

  private static String joinNames(List<String> names) {
    if (names.size() <= 1) {
      return String.join("", names);
    }
    return String.join(", ", names.subList(0, names.size() - 1))
        + " and "
        + names.get(names.size() - 1);
  }

  private static List<String> lanesTable(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + LANES,
                "",
                "| lane | who won | laning window | creep score and denies | XP and level | key"
                    + " facts | assistant | owner |",
                "|---|---|---|---|---|---|---|---|"));
    for (int id : pairIds(d)) {
      List<LaneResultDomain> sides = sides(d, id);
      out.add(
          "| %s | %s | %s | %s | %s | %s | | |"
              .formatted(
                  ReviewLoopReport.tableCell(laneLabel(d, id)),
                  ReviewLoopReport.tableCell(verdict(sides)),
                  ReviewLoopReport.tableCell(windows(sides)),
                  ReviewLoopReport.tableCell(creeps(sides)),
                  ReviewLoopReport.tableCell(xpAndLevel(sides)),
                  ReviewLoopReport.tableCell(facts(d, id, sides))));
    }
    return out;
  }

  private static String windows(List<LaneResultDomain> sides) {
    LaneResultDomain first = sides.get(0);
    return "lane %s to %s (%s); %s"
        .formatted(
            LoopWording.clock(first.getStartSeconds()),
            LoopWording.clock(first.getEndSeconds()),
            endWords(first.getEndReason()),
            sides.stream()
                .map(
                    r ->
                        "%s side left at %s (%s)"
                            .formatted(
                                LoopWording.team(r.getTeam()),
                                LoopWording.clock(r.getSideEndSeconds()),
                                endWords(r.getSideEndReason())))
                .collect(Collectors.joining(", ")));
  }

  private static String endWords(String stored) {
    LaneEnd end = Enums.find(LaneEnd.class, stored);
    return end == null ? "?" : end.words();
  }

  private static String creeps(List<LaneResultDomain> sides) {
    return sides.stream()
        .map(
            r ->
                "%s %d (%d last hits, %d denies, %d siege)"
                    .formatted(
                        LoopWording.team(r.getTeam()),
                        Math.round(r.getCreepScore()),
                        r.getLastHits(),
                        r.getDenies(),
                        r.getSiegeLastHits()))
        .collect(Collectors.joining(" vs "));
  }

  private static String xpAndLevel(List<LaneResultDomain> sides) {
    return sides.stream()
        .map(
            r ->
                "%s %s XP, level %s"
                    .formatted(
                        LoopWording.team(r.getTeam()),
                        r.getXp() == null ? "?" : LoopWording.gold(r.getXp()),
                        r.getLevel() == null
                            ? "?"
                            : String.format(Locale.ROOT, "%.1f", r.getLevel())))
        .collect(Collectors.joining(" vs "));
  }

  private static String facts(MatchData d, int pairId, List<LaneResultDomain> sides) {
    List<String> facts = new ArrayList<>();
    facts.add("kills " + perSide(sides, r -> String.valueOf(r.getKills())));
    facts.add("hero damage dealt " + perSide(sides, r -> String.valueOf(r.getHeroDamageDealt())));
    sides.stream()
        .filter(r -> Boolean.TRUE.equals(r.getOwnTowerFell()))
        .forEach(r -> facts.add("the %s lane tower fell".formatted(LoopWording.team(r.getTeam()))));
    sides.forEach(r -> ultimatePhrase(d, pairId, r.getTeam()).ifPresent(facts::add));
    return String.join("; ", facts);
  }

  /** One phrase for a side: all had the ultimate, or who did not reach the level. */
  private static Optional<String> ultimatePhrase(MatchData d, int pairId, String team) {
    List<LaneAssignmentDomain> heroes =
        d.assignments().stream()
            .filter(a -> Objects.equals(a.getPairId(), pairId) && team.equals(a.getTeam()))
            .sorted(Comparator.comparing(LaneAssignmentDomain::getPlayerSlot))
            .toList();
    Map<Long, LanePlayerStatDomain> stats = statsBySlot(d);
    List<String> lacking = new ArrayList<>();
    int known = 0;
    for (LaneAssignmentDomain a : heroes) {
      LanePlayerStatDomain s = stats.get(a.getPlayerSlot());
      if (s == null || s.getLevel() == null) {
        continue;
      }
      known++;
      if (s.getUltimateSeconds() == null) {
        lacking.add(
            "%s's %s did not reach level %d"
                .formatted(
                    LoopWording.team(team),
                    LoopWording.hero(d.heroNames(), a.getPlayerSlot()),
                    d.ultimateLevel()));
      }
    }
    if (known == 0) {
      return Optional.empty();
    }
    if (!lacking.isEmpty()) {
      return Optional.of(String.join(", ", lacking));
    }
    return Optional.of(
        "%s: %s had the ultimate"
            .formatted(
                LoopWording.team(team),
                known == 1 ? "the hero" : known == 2 ? "both heroes" : "all heroes"));
  }

  private static Map<Long, LanePlayerStatDomain> statsBySlot(MatchData d) {
    return d.stats().stream()
        .collect(Collectors.toMap(LanePlayerStatDomain::getPlayerSlot, s -> s, (a, b) -> a));
  }

  private static String ultimateText(LanePlayerStatDomain s, int level) {
    if (s.getLevel() == null) {
      return "level %d not known".formatted(level);
    }
    return s.getUltimateSeconds() == null
        ? "did not reach level %d when the lane ended (level %d)".formatted(level, s.getLevel())
        : "reached level %d at %s, before the lane ended"
            .formatted(level, LoopWording.clock(s.getUltimateSeconds()));
  }

  private static String perSide(
      List<LaneResultDomain> sides, Function<LaneResultDomain, String> value) {
    return sides.stream()
        .map(r -> "%s %s".formatted(LoopWording.team(r.getTeam()), value.apply(r)))
        .collect(Collectors.joining(", "));
  }

  private static List<String> playersTable(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + PLAYERS,
                "",
                "| hero | lane and window | last hits | denies | creep score and share | XP and"
                    + " level | kills and deaths | assistant | owner |",
                "|---|---|---|---|---|---|---|---|---|"));
    Map<Long, LanePlayerStatDomain> stats =
        d.stats().stream()
            .collect(Collectors.toMap(LanePlayerStatDomain::getPlayerSlot, s -> s, (a, b) -> a));
    d.assignments().stream()
        .sorted(Comparator.comparing(LaneAssignmentDomain::getPlayerSlot))
        .forEach(
            a -> {
              String hero = LoopWording.hero(d.heroNames(), a.getPlayerSlot());
              LanePlayerStatDomain s = stats.get(a.getPlayerSlot());
              if (a.getLane() == null || s == null) {
                out.add(
                    "| %s | no lane (roaming, jungling or not seen in a lane) | | | | | | | |"
                        .formatted(ReviewLoopReport.tableCell(hero)));
                return;
              }
              out.add(
                  "| %s | %s | %s | %d | %s | %s | %s | | |"
                      .formatted(
                          ReviewLoopReport.tableCell(hero),
                          ReviewLoopReport.tableCell(
                              "%s %s lane, %s to %s (%s)"
                                  .formatted(
                                      LoopWording.team(a.getTeam()),
                                      positionWords(a.getPosition()),
                                      LoopWording.clock(a.getStartSeconds()),
                                      LoopWording.clock(a.getEndSeconds()),
                                      endWords(a.getEndReason()))),
                          "%d (%d melee, %d ranged, %d siege%s)"
                              .formatted(
                                  s.getLastHits(),
                                  s.getMeleeLastHits(),
                                  s.getRangedLastHits(),
                                  s.getSiegeLastHits(),
                                  s.getOtherLastHits() == null || s.getOtherLastHits() == 0
                                      ? ""
                                      : ", %d other".formatted(s.getOtherLastHits())),
                          s.getDenies(),
                          "%d, %d%% of his side"
                              .formatted(
                                  Math.round(s.getCreepScore()),
                                  Math.round(s.getSideShare() * 100)),
                          "%s XP, level %s; %s"
                              .formatted(
                                  s.getXp() == null ? "?" : LoopWording.gold(s.getXp()),
                                  s.getLevel() == null ? "?" : s.getLevel(),
                                  ultimateText(s, d.ultimateLevel())),
                          "kills %d, deaths %d".formatted(s.getKills(), s.getDeaths())));
            });
    return out;
  }

  private static List<String> whyTable(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + WHY,
                "",
                "| reason | rank | severity | assistant | owner |",
                "|---|---|---|---|---|"));
    Map<String, String> results =
        d.results().stream()
            .collect(
                Collectors.toMap(
                    r -> r.getPairId() + "/" + r.getTeam(),
                    LaneResultDomain::getResult,
                    (a, b) -> a));
    d.reasons().stream()
        .sorted(
            Comparator.comparing(LaneReasonDomain::getPairId)
                .thenComparing(LaneReasonDomain::getTeam)
                .thenComparing(LaneReasonDomain::getRank))
        .forEach(
            r ->
                out.add(
                    "| %s | %d | %s | | |"
                        .formatted(
                            ReviewLoopReport.tableCell(
                                sentence(d, r, results.get(r.getPairId() + "/" + r.getTeam()))),
                            r.getRank(),
                            String.format(Locale.ROOT, "%.2f", r.getSeverity()))));
    return out;
  }

  private static String sentence(MatchData d, LaneReasonDomain r, String result) {
    String evidence = r.getEvidence() == null ? "" : r.getEvidence();
    String lead =
        evidence.isEmpty()
            ? evidence
            : Character.toUpperCase(evidence.charAt(0)) + evidence.substring(1);
    return "%s %s lane (%s) was %s: %s"
        .formatted(
            LoopWording.team(r.getTeam()),
            sideOf(d, r),
            words(result),
            "HELPED".equals(r.getDirection()) ? "helped" : "hurt",
            lead);
  }

  private static String sideOf(MatchData d, LaneReasonDomain r) {
    return d.results().stream()
        .filter(x -> x.getPairId().equals(r.getPairId()) && x.getTeam().equals(r.getTeam()))
        .map(x -> positionWords(x.getPosition()))
        .findFirst()
        .orElse("?");
  }
}
