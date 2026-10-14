package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who won the opening of the game and by how much, the bounty runes of each team with who took the
 * first ones, and the power and water runes taken in the mid lane. At most three lines under a
 * header; a match with no {@code opening_summary} prints nothing. It reads {@code opening_summary}
 * and {@code rune_pickup} only, so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class OpeningSection implements ReplayReportSection {
  private static final Comparator<RunePickupDomain> CHRONOLOGICAL =
      Comparator.comparing(
              RunePickupDomain::getSeconds, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              RunePickupDomain::getPlayerSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              RunePickupDomain::getRuneIndex, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(RunePickupDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private static final int SECONDS_PER_MINUTE = 60;
  private static final String LEVEL = "The opening was level: gold, XP and kills were level.";

  private final OpeningSummaryRepo summaryRepo;
  private final RunePickupRepo pickupRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "opening";
  }

  @Override
  public int order() {
    return 18;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<OpeningSummaryDomain> summaries = present(summaryRepo.findByMatchId(input.matchId()));
    if (summaries.isEmpty()) {
      return List.of();
    }
    List<RunePickupDomain> pickups =
        present(pickupRepo.findByMatchId(input.matchId())).stream().sorted(CHRONOLOGICAL).toList();
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> body = new ArrayList<>();
    result(summaries, yours).ifPresent(body::add);
    bounty(pickups, heroes, yours).ifPresent(body::add);
    midLane(pickups, heroes, properties.getOpeningRuneMinutes()).ifPresent(body::add);
    if (body.isEmpty()) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    lines.add("**Opening and runes**");
    lines.addAll(body);
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxOpeningLineLength());
    return lines.stream().map(line -> LineText.cut(line, max)).toList();
  }

  private static <T> List<T> present(List<T> rows) {
    return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
  }

  private static Optional<String> result(List<OpeningSummaryDomain> summaries, TeamSide yours) {
    OpeningSummaryDomain won = withResult(summaries, "WON");
    OpeningSummaryDomain lost = withResult(summaries, "LOST");
    if (won == null && lost == null) {
      return summaries.stream().anyMatch(s -> "DRAW".equals(s.getResult()))
          ? Optional.of(LEVEL)
          : Optional.empty();
    }
    TeamSide winner =
        won != null
            ? Enums.find(TeamSide.class, won.getTeam())
            : opponent(Enums.find(TeamSide.class, lost.getTeam()));
    if (winner == null) {
      return Optional.empty();
    }
    Integer winnerKills = killsOf(summaries, winner);
    Integer enemyKills = killsOf(summaries, winner.opponent());
    Long balance =
        won != null ? won.getBalance() : lost.getBalance() == null ? null : -lost.getBalance();
    boolean knownKills = winnerKills != null && enemyKills != null;
    String kills = knownKills ? "%d to %d".formatted(winnerKills, enemyKills) : "";
    String team = Prettify.teamOf(winner.name(), yours);
    String took = OpeningWording.result("WON");
    if (balance != null && balance == 0) {
      if (!knownKills) {
        return Optional.of("The opening was level in gold and XP.");
      }
      return winnerKills.equals(enemyKills)
          ? Optional.of(LEVEL)
          : Optional.of(
              "%s %s on kills, %s, after gold and XP were level.".formatted(team, took, kills));
    }
    String by =
        balance == null
            ? ""
            : " by %s gold and XP".formatted(BehaviourWording.gold(Math.abs(balance)));
    return Optional.of(
        kills.isEmpty()
            ? "%s %s%s.".formatted(team, took, by)
            : "%s %s%s; kills %s.".formatted(team, took, by, kills));
  }

  private static TeamSide opponent(TeamSide side) {
    return side == null ? null : side.opponent();
  }

  private static Integer killsOf(List<OpeningSummaryDomain> summaries, TeamSide side) {
    return summaries.stream()
        .filter(s -> Enums.find(TeamSide.class, s.getTeam()) == side)
        .map(OpeningSummaryDomain::getKills)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static OpeningSummaryDomain withResult(
      List<OpeningSummaryDomain> summaries, String result) {
    return summaries.stream()
        .filter(s -> result.equals(s.getResult()))
        .min(
            Comparator.comparing(
                    OpeningSummaryDomain::getTeam, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    OpeningSummaryDomain::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .orElse(null);
  }

  private static Optional<String> bounty(
      List<RunePickupDomain> pickups, Map<Long, String> heroes, TeamSide yours) {
    List<String> parts = new ArrayList<>();
    for (TeamSide side : TeamSide.values()) {
      List<RunePickupDomain> taken =
          pickups.stream()
              .filter(p -> Enums.find(RuneType.class, p.getRuneType()) == RuneType.BOUNTY)
              .filter(p -> side(p) == side)
              .toList();
      if (!taken.isEmpty()) {
        parts.add(bountyOf(side, taken, heroes, yours));
      }
    }
    return parts.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", parts) + ".");
  }

  private static String bountyOf(
      TeamSide side, List<RunePickupDomain> taken, Map<Long, String> heroes, TeamSide yours) {
    List<Integer> golds = taken.stream().map(RunePickupDomain::getGold).toList();
    String gold =
        golds.contains(null)
            ? ""
            : " worth %s gold in total"
                .formatted(BehaviourWording.gold(golds.stream().mapToInt(i -> i).sum()));
    RunePickupDomain first = taken.get(0);
    return "%s took %d bounty %s%s, the first was %s at %s"
        .formatted(
            Prettify.teamOf(side.name(), yours),
            taken.size(),
            taken.size() == 1 ? "rune" : "runes",
            gold,
            BehaviourWording.hero(heroes, first.getPlayerSlot()),
            time(first.getSeconds()));
  }

  private record Taker(Long slot, RuneType type) {}

  /** One clause per hero and rune kind, in the order of the first pickup, times in order. */
  private static Optional<String> midLane(
      List<RunePickupDomain> pickups, Map<Long, String> heroes, int minutes) {
    Map<Taker, List<String>> times = new LinkedHashMap<>();
    pickups.stream()
        .filter(p -> Boolean.TRUE.equals(p.getMidLane()))
        .filter(p -> p.getSeconds() != null && p.getSeconds() <= minutes * SECONDS_PER_MINUTE)
        .filter(OpeningSection::powerOrWater)
        .forEach(
            p ->
                times
                    .computeIfAbsent(
                        new Taker(p.getPlayerSlot(), Enums.find(RuneType.class, p.getRuneType())),
                        key -> new ArrayList<>())
                    .add(time(p.getSeconds())));
    if (times.isEmpty()) {
      return Optional.empty();
    }
    List<String> clauses = new ArrayList<>();
    times.forEach(
        (taker, at) ->
            clauses.add(
                "%s took %s at %s"
                    .formatted(
                        BehaviourWording.hero(heroes, taker.slot()),
                        runes(taker.type(), at.size()),
                        LaneWording.joinNames(at))));
    return Optional.of(
        "In the mid lane in the first %d minutes: ".formatted(minutes)
            + String.join("; ", clauses)
            + ".");
  }

  private static String runes(RuneType type, int count) {
    String words = type.words();
    if (count > 1) {
      return words + " runes";
    }
    return ("aeiou".indexOf(words.charAt(0)) >= 0 ? "an " : "a ") + words + " rune";
  }

  private static boolean powerOrWater(RunePickupDomain p) {
    RuneType type = Enums.find(RuneType.class, p.getRuneType());
    return type != null && (type == RuneType.WATER || type.isFightEffect());
  }

  private static TeamSide side(RunePickupDomain p) {
    TeamSide side = Enums.find(TeamSide.class, p.getTeam());
    if (side == null && p.getPlayerSlot() != null) {
      return TeamSide.ofSlot(p.getPlayerSlot());
    }
    return side;
  }

  private static String time(Integer seconds) {
    return seconds == null ? "?" : GameTime.clock(seconds);
  }
}
