package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import com.ako.dbuff.service.replay.analysis.lane.LaneReason;
import com.ako.dbuff.service.replay.analysis.lane.LaneResult;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The laning stage, one line per lane (bottom, middle, top): who won and how clearly, the heroes of
 * both sides, how long the lane lasted and the strongest reasons for the result. Lanes with no
 * winner are listed as even. It reads the lane tables only, so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class LanesSection implements ReplayReportSection {
  private static final int MAX_REASONS = 2;
  private static final String ELLIPSIS = "...";
  private static final String UNKNOWN_HERO = "an unknown hero";
  private static final Map<String, Integer> LANE_ORDER = Map.of("BOTTOM", 0, "MID", 1, "TOP", 2);
  private static final Map<String, String> LANE_WORDS =
      Map.of("BOTTOM", "Bottom", "MID", "Mid", "TOP", "Top");

  private final LaneResultRepo resultRepo;
  private final LaneAssignmentRepo assignmentRepo;
  private final LaneReasonRepo reasonRepo;
  private final ReportHeroNames heroNames;
  private final LaneProperties laneProperties;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "lanes";
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    Map<Integer, Map<TeamSide, LaneResultDomain>> lanes = lanes(input.matchId());
    if (lanes.isEmpty()) {
      return List.of();
    }
    List<LaneAssignmentDomain> assignments = assignmentRepo.findByMatchId(input.matchId());
    List<LaneReasonDomain> reasons = reasonRepo.findByMatchId(input.matchId());
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> lines = new ArrayList<>();
    lines.add("**Laning stage**");
    for (Map.Entry<Integer, Map<TeamSide, LaneResultDomain>> lane : lanes.entrySet()) {
      lines.add(cut(line(lane.getKey(), lane.getValue(), assignments, reasons, heroes, yours)));
    }
    return lines;
  }

  /** The rows of each lane by side, in bottom, mid, top order; rows that cannot be read are out. */
  private Map<Integer, Map<TeamSide, LaneResultDomain>> lanes(long matchId) {
    Map<Integer, Map<TeamSide, LaneResultDomain>> byPair = new TreeMap<>();
    for (LaneResultDomain row : resultRepo.findByMatchId(matchId)) {
      TeamSide side = Enums.find(TeamSide.class, row.getTeam());
      if (row.getPairId() == null
          || side == null
          || row.getLane() == null
          || !LANE_ORDER.containsKey(row.getLane())) {
        continue;
      }
      byPair.computeIfAbsent(row.getPairId(), k -> new EnumMap<>(TeamSide.class)).put(side, row);
    }
    return byPair.entrySet().stream()
        .sorted(Comparator.comparing(e -> laneIndex(e.getValue())))
        .limit(LANE_ORDER.size())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private static int laneIndex(Map<TeamSide, LaneResultDomain> sides) {
    return LANE_ORDER.get(any(sides).getLane());
  }

  private static LaneResultDomain any(Map<TeamSide, LaneResultDomain> sides) {
    return sides.values().iterator().next();
  }

  private String line(
      int pairId,
      Map<TeamSide, LaneResultDomain> sides,
      List<LaneAssignmentDomain> assignments,
      List<LaneReasonDomain> reasons,
      Map<Long, String> heroes,
      TeamSide yours) {
    LaneResultDomain first = any(sides);
    StringBuilder text = new StringBuilder(LANE_WORDS.get(first.getLane())).append(" lane");
    duration(first).ifPresent(d -> text.append(" (lasted ").append(d).append(")"));
    text.append(": ").append(verdict(sides, yours)).append(". ");
    text.append(heroLine(pairId, sides, assignments, heroes));
    List<LaneReasonDomain> forPair =
        reasons.stream().filter(r -> Objects.equals(r.getPairId(), pairId)).toList();
    String why = why(sides, forPair);
    if (!why.isEmpty()) {
      text.append(" Why: ").append(why).append(".");
    }
    levelNote(forPair).ifPresent(n -> text.append(" ").append(n).append("."));
    return text.toString();
  }

  private static Optional<String> duration(LaneResultDomain row) {
    if (row.getStartSeconds() == null || row.getEndSeconds() == null) {
      return Optional.empty();
    }
    return Optional.of(GameTime.clock(Math.max(0, row.getEndSeconds() - row.getStartSeconds())));
  }

  private String verdict(Map<TeamSide, LaneResultDomain> sides, TeamSide yours) {
    LaneResultDomain winner = winner(sides);
    if (winner != null) {
      TeamSide side = TeamSide.valueOf(winner.getTeam());
      String how =
          winner.getMargin() == null
              ? ""
              : " "
                  + LaneWording.strength(
                      winner.getMargin(),
                      properties.getLaneClearMargin(),
                      properties.getLaneBigMargin());
      return Prettify.team(side.name()) + (side == yours ? " (your team)" : "") + " won" + how;
    }
    LaneResultDomain first = any(sides);
    if (sides.size() == 1
        && Enums.find(LaneResult.class, first.getResult()) == LaneResult.UNCONTESTED) {
      return Prettify.team(first.getTeam()) + " was alone";
    }
    return "even";
  }

  private static LaneResultDomain winner(Map<TeamSide, LaneResultDomain> sides) {
    return sides.values().stream()
        .filter(r -> Enums.find(LaneResult.class, r.getResult()) == LaneResult.WON)
        .findFirst()
        .orElse(null);
  }

  private static String heroLine(
      int pairId,
      Map<TeamSide, LaneResultDomain> sides,
      List<LaneAssignmentDomain> assignments,
      Map<Long, String> heroes) {
    List<String> parts = new ArrayList<>();
    for (TeamSide side : sides.keySet()) {
      List<String> names =
          assignments.stream()
              .filter(a -> Objects.equals(a.getPairId(), pairId) && a.getPlayerSlot() != null)
              .filter(a -> side == Enums.find(TeamSide.class, a.getTeam()))
              .sorted(Comparator.comparing(LaneAssignmentDomain::getPlayerSlot))
              .map(a -> heroes.getOrDefault(a.getPlayerSlot(), UNKNOWN_HERO))
              .toList();
      if (!names.isEmpty()) {
        parts.add(Prettify.team(side.name()) + " " + LaneWording.joinNames(names));
      }
    }
    return parts.isEmpty() ? "" : String.join(", ", parts) + ".";
  }

  /**
   * What helped the winner and what hurt the loser, strongest first, one per code (the two sides
   * state the same fact); nothing for an even lane.
   */
  private String why(Map<TeamSide, LaneResultDomain> sides, List<LaneReasonDomain> reasons) {
    LaneResultDomain winner = winner(sides);
    if (winner == null) {
      return "";
    }
    Set<String> codes = new HashSet<>();
    return reasons.stream()
        .filter(r -> !LaneReason.NO_ULTIMATE_AT_LANE_END.equals(r.getCode()))
        .filter(r -> r.getEvidence() != null && !r.getEvidence().isBlank())
        .filter(r -> r.getTeam() != null)
        .filter(r -> explainsResult(r, winner.getTeam()))
        .sorted(
            Comparator.comparing(
                    (LaneReasonDomain r) -> r.getSeverity() == null ? 0 : r.getSeverity())
                .reversed())
        .filter(r -> codes.add(String.valueOf(r.getCode())))
        .map(r -> evidence(r.getEvidence()))
        .limit(MAX_REASONS)
        .collect(Collectors.joining("; "));
  }

  private static boolean explainsResult(LaneReasonDomain reason, String winnerTeam) {
    boolean ofWinner = winnerTeam.equals(reason.getTeam());
    return ofWinner
        ? LaneReason.Direction.HELPED.name().equals(reason.getDirection())
        : LaneReason.Direction.HURT.name().equals(reason.getDirection());
  }

  private static String evidence(String stored) {
    String text = stored.strip();
    return LaneWording.capitalised(
        text.endsWith(".") ? text.substring(0, text.length() - 1) : text);
  }

  private Optional<String> levelNote(List<LaneReasonDomain> reasons) {
    return reasons.stream()
        .filter(r -> LaneReason.NO_ULTIMATE_AT_LANE_END.equals(r.getCode()))
        .filter(r -> LaneReason.Direction.HELPED.name().equals(r.getDirection()))
        .map(r -> Enums.find(TeamSide.class, r.getTeam()))
        .filter(Objects::nonNull)
        .findFirst()
        .map(
            side ->
                "%s reached level %d when the lane ended, %s did not"
                    .formatted(
                        Prettify.team(side.name()),
                        laneProperties.getUltimateLevel(),
                        Prettify.team(side.opponent().name())));
  }

  private String cut(String line) {
    int max = Math.max(ELLIPSIS.length() + 1, properties.getMaxLaneLineLength());
    return line.length() <= max ? line : line.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
  }
}
