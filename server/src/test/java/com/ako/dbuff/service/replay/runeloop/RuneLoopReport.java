package com.ako.dbuff.service.replay.runeloop;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneProperties;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.OpeningWording;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The review report of one stored match, as markdown. It only lays out what the tables hold; the
 * three tables headed {@link #OPENING}, {@link #RUNES} and {@link #ADVANTAGE} are what {@link
 * RuneLoopTopic} lets the loop compare between rounds. The verdict columns are left empty for the
 * reviewers.
 */
final class RuneLoopReport {
  static final String OPENING = "Opening";
  static final String RUNES = "Runes";
  static final String ADVANTAGE = "Rune advantage in fights";

  private static final String RUNE_ADVANTAGE = "RUNE_ADVANTAGE";
  private static final String ENEMY_RUNE_ADVANTAGE = "ENEMY_RUNE_ADVANTAGE";
  private static final String YES = "yes";
  private static final String NO = "no";

  private static final String HOW_TO_READ =
      "\"Opening\" is how each team did from 30 s before to 90 s after the first (0:00) bounty rune"
          + " spawn: who won it (gold plus experience first, kills break a tie), the runes it took,"
          + " the bounty gold, kills, and the gold and experience it received in that window."
          + " \"Runes\" lists every rune taken, with who took it, where he stood, the bounty gold"
          + " it paid, whether he was a mid laner (a proxy: in the mid lane at the pickup, or an"
          + " early pickup of a hero whose main lane of the first 4 minutes is mid) and, for a"
          + " power rune, whether an important fight started while its effect lasted. \"Rune"
          + " advantage in fights\" lists the fights where a hero fought under a power rune, with"
          + " who won. Times are the game clock (m:ss). Write OK or REJECT in the assistant and"
          + " owner columns.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<RunePickupDomain> pickups,
      List<OpeningSummaryDomain> summaries,
      List<FightDomain> fights,
      List<FightImportanceDomain> importance,
      List<FightReasonDomain> reasons,
      RuneProperties properties) {}

  private RuneLoopReport() {}

  static String render(MatchData d) {
    List<RunePickupDomain> pickups =
        d.pickups().stream()
            .sorted(
                Comparator.comparing(RunePickupDomain::getSeconds)
                    .thenComparing(RunePickupDomain::getPlayerSlot))
            .toList();
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(d, pickups)));
    out.add("");
    out.addAll(openingTable(d));
    out.add("");
    out.addAll(runesTable(d, pickups));
    out.add("");
    out.addAll(advantageTable(d));
    return String.join("\n", out) + "\n";
  }

  private static String summary(MatchData d, List<RunePickupDomain> pickups) {
    if (pickups.isEmpty() && d.summaries().isEmpty()) {
      return "no runes and no opening stored (the analysis did not run, or the match has no rune"
          + " log).";
    }
    Map<String, Long> byType =
        pickups.stream()
            .collect(Collectors.groupingBy(RunePickupDomain::getRuneType, Collectors.counting()));
    long power = pickups.stream().filter(p -> isPower(p)).count();
    long inImportant =
        pickups.stream().filter(p -> isPower(p) && !windowFights(d, p).isEmpty()).count();
    long advantage = d.reasons().stream().filter(r -> RUNE_ADVANTAGE.equals(r.getCode())).count();
    return "%d runes taken (%s); opening: %s; %d of %d power runes had an important fight in their"
            .formatted(
                pickups.size(),
                byType.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue() + " " + LoopWording.rune(e.getKey()))
                    .collect(Collectors.joining(", ")),
                openingVerdict(d),
                inImportant,
                power)
        + " effect window; %d rune advantage reasons stored.".formatted(advantage);
  }

  private static String openingVerdict(MatchData d) {
    Optional<OpeningSummaryDomain> winner =
        d.summaries().stream().filter(s -> "WON".equals(s.getResult())).findFirst();
    if (d.summaries().isEmpty()) {
      return "not stored";
    }
    return winner
        .map(s -> "%s won it (%s)".formatted(LoopWording.team(s.getTeam()), balanceText(s)))
        .orElse("a draw");
  }

  private static String balanceText(OpeningSummaryDomain s) {
    return "%s gold and XP, kills %s".formatted(LoopWording.gold(s.getBalance()), s.getKills());
  }

  private static boolean isPower(RunePickupDomain pickup) {
    RuneType type = Enums.find(RuneType.class, pickup.getRuneType());
    return type != null && type.isFightEffect();
  }

  private static List<String> openingTable(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + OPENING,
                "",
                "| team | result | first rune | runes taken | bounty gold | bounty gold, whole game"
                    + " | kills | gold | XP | gold and XP balance | assistant | owner |",
                "|---|---|---|---|---|---|---|---|---|---|---|---|"));
    if (d.summaries().isEmpty()) {
      return List.of(
          "## " + OPENING,
          "",
          "**No opening stored for this match (the combat log has no gold reasons, or the"
              + " analysis did not run).**");
    }
    Optional<Integer> firstSecond =
        d.summaries().stream()
            .map(OpeningSummaryDomain::getFirstTakerSeconds)
            .filter(s -> s != null)
            .min(Integer::compare);
    d.summaries().stream()
        .sorted(Comparator.comparing(OpeningSummaryDomain::getTeam))
        .forEach(
            s ->
                out.add(
                    "| %s | %s | %s | %d | %s | %s | %d | %s | %s | %s | | |"
                        .formatted(
                            LoopWording.team(s.getTeam()),
                            OpeningWording.result(s.getResult()),
                            firstRuneText(d, s, firstSecond),
                            s.getRunesTaken(),
                            LoopWording.gold(s.getBountyGold()),
                            LoopWording.gold(s.getBountyGoldGame()),
                            s.getKills(),
                            LoopWording.gold(s.getGold()),
                            LoopWording.gold(s.getXp()),
                            OpeningWording.signedGold(s.getBalance()))));
    return out;
  }

  private static String firstRuneText(
      MatchData d, OpeningSummaryDomain s, Optional<Integer> firstSecond) {
    if (s.getFirstTakerSlot() == null) {
      return "none taken";
    }
    String text =
        "%s at %s"
            .formatted(
                LoopWording.hero(d.heroNames(), s.getFirstTakerSlot()),
                LoopWording.clock(s.getFirstTakerSeconds()));
    return firstSecond.isPresent() && firstSecond.get().equals(s.getFirstTakerSeconds())
        ? "first of the game: " + text
        : text;
  }

  private static List<String> runesTable(MatchData d, List<RunePickupDomain> pickups) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + RUNES,
                "",
                "| rune | team | gold | where | mid lane | important fight in the effect window |"
                    + " assistant | owner |",
                "|---|---|---|---|---|---|---|---|"));
    for (RunePickupDomain p : pickups) {
      out.add(
          "| %s | %s | %s | %s | %s | %s | | |"
              .formatted(
                  ReviewLoopReport.tableCell(runeSentence(d, p)),
                  LoopWording.team(p.getTeam()),
                  p.getGold() == null ? "-" : LoopWording.gold(p.getGold()),
                  LoopWording.place(p.getZone(), p.getLane()),
                  Boolean.TRUE.equals(p.getMidLane()) ? YES : NO,
                  ReviewLoopReport.tableCell(fightWindowText(d, p))));
    }
    return out;
  }

  private static String runeSentence(MatchData d, RunePickupDomain p) {
    return "%s took the %s rune at %s"
        .formatted(
            LoopWording.hero(d.heroNames(), p.getPlayerSlot()),
            LoopWording.rune(p.getRuneType()),
            LoopWording.clock(p.getSeconds()));
  }

  private static String fightWindowText(MatchData d, RunePickupDomain p) {
    if (!isPower(p)) {
      return "not a power rune";
    }
    List<FightDomain> fights = windowFights(d, p);
    return fights.isEmpty()
        ? NO
        : YES
            + ": "
            + fights.stream().map(RuneLoopReport::fightLabel).collect(Collectors.joining(", "));
  }

  /** The important fights that overlap the time the rune's effect lasts. */
  private static List<FightDomain> windowFights(MatchData d, RunePickupDomain p) {
    RuneType type = Enums.find(RuneType.class, p.getRuneType());
    int effect = type == null ? 0 : d.properties().effectSeconds(type);
    Set<Integer> important =
        d.importance().stream()
            .filter(FightImportanceDomain::isImportant)
            .map(FightImportanceDomain::getFightIndex)
            .collect(Collectors.toSet());
    return d.fights().stream()
        .filter(f -> f.getFightIndex() != null && important.contains(f.getFightIndex()))
        .filter(f -> f.getStartSeconds() != null)
        .filter(
            f ->
                f.getStartSeconds() <= p.getSeconds() + effect
                    && (f.getEndSeconds() == null ? f.getStartSeconds() : f.getEndSeconds())
                        >= p.getSeconds())
        .sorted(Comparator.comparing(FightDomain::getFightIndex))
        .toList();
  }

  private static String fightLabel(FightDomain f) {
    return "fight %d at %s".formatted(f.getFightIndex(), LoopWording.clock(f.getStartSeconds()));
  }

  private static List<String> advantageTable(MatchData d) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + ADVANTAGE,
                "",
                "| rune advantage | fight | winner | important | the other side's list says |"
                    + " severity | assistant | owner |",
                "|---|---|---|---|---|---|---|---|"));
    Map<Integer, FightDomain> fights =
        d.fights().stream()
            .filter(f -> f.getFightIndex() != null)
            .collect(Collectors.toMap(FightDomain::getFightIndex, f -> f, (a, b) -> a));
    Set<Integer> important =
        d.importance().stream()
            .filter(FightImportanceDomain::isImportant)
            .map(FightImportanceDomain::getFightIndex)
            .collect(Collectors.toSet());
    d.reasons().stream()
        .filter(r -> RUNE_ADVANTAGE.equals(r.getCode()) && fights.containsKey(r.getFightIndex()))
        .sorted(
            Comparator.comparing(FightReasonDomain::getFightIndex)
                .thenComparing(FightReasonDomain::getSubjectSlot))
        .forEach(
            r -> {
              FightDomain f = fights.get(r.getFightIndex());
              String hero = LoopWording.hero(d.heroNames(), r.getSubjectSlot());
              out.add(
                  "| %s | %s | %s | %s | %s | %s | | |"
                      .formatted(
                          ReviewLoopReport.tableCell(LoopWording.reason(r, hero)),
                          fightLabel(f),
                          winnerText(f),
                          important.contains(r.getFightIndex()) ? YES : NO,
                          enemySays(d, r),
                          String.format(Locale.ROOT, "%.2f", r.getSeverity())));
            });
    return out;
  }

  private static String winnerText(FightDomain f) {
    TeamSide winner = Enums.find(TeamSide.class, f.getWinner());
    return winner == null ? "no winner" : LoopWording.team(winner) + " won";
  }

  private static String enemySays(MatchData d, FightReasonDomain advantage) {
    return d.reasons().stream()
        .filter(r -> ENEMY_RUNE_ADVANTAGE.equals(r.getCode()))
        .filter(r -> r.getFightIndex().equals(advantage.getFightIndex()))
        .filter(
            r ->
                r.getEvidence() != null
                    && advantage.getEvidence() != null
                    && r.getEvidence().contains(advantage.getEvidence()))
        .findFirst()
        .map(r -> LoopWording.team(r.getTeam()) + " list: yes")
        .orElse("no");
  }
}
