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
import com.ako.dbuff.service.replay.map.Zone;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  private static final String UNKNOWN_HERO = "an unknown hero";

  private final LaneResultRepo resultRepo;
  private final LaneAssignmentRepo assignmentRepo;
  private final LaneReasonRepo reasonRepo;
  private final ReportHeroNames heroNames;
  private final LaneProperties laneProperties;
  private final ReplayReportProperties properties;

  private record Pair(Zone.Lane lane, int pairId, Map<TeamSide, LaneResultDomain> sides) {}

  private record Winner(TeamSide side, LaneResultDomain row) {}

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
    return render(input, pairs(input.matchId()));
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<Pair> pairs = pairs(input.matchId());
    return SectionViews.titled(render(input, pairs), lanesVerdict(pairs, input));
  }

  private Verdict lanesVerdict(List<Pair> pairs, ReplayReportInput input) {
    long radiant = wins(pairs, TeamSide.RADIANT);
    long dire = wins(pairs, TeamSide.DIRE);
    if (radiant + dire == 0) {
      return new Verdict("No lane had a clear winner", Accent.NEUTRAL);
    }
    Verdict decided =
        SectionViews.contest(
            TeamSide.commonSide(input.focusSlots()),
            radiant,
            dire,
            radiant + dire,
            "decided lanes");
    long even = pairs.size() - radiant - dire;
    return even == 0
        ? decided
        : new Verdict(
            decided.text() + "; " + SectionViews.count(even, "lane") + " even", decided.accent());
  }

  private static long wins(List<Pair> pairs, TeamSide side) {
    return pairs.stream()
        .map(p -> winner(p.sides()))
        .filter(w -> w != null && w.side() == side)
        .count();
  }

  private List<String> render(ReplayReportInput input, List<Pair> pairs) {
    if (pairs.isEmpty()) {
      return List.of();
    }
    Map<Integer, List<LaneAssignmentDomain>> assignments =
        assignmentRepo.findByMatchId(input.matchId()).stream()
            .filter(a -> a.getPairId() != null)
            .collect(Collectors.groupingBy(LaneAssignmentDomain::getPairId));
    Map<Integer, List<LaneReasonDomain>> reasons =
        pairs.stream().anyMatch(p -> winner(p.sides()) != null)
            ? reasonRepo.findByMatchId(input.matchId()).stream()
                .filter(r -> r.getPairId() != null)
                .collect(Collectors.groupingBy(LaneReasonDomain::getPairId))
            : Map.of();
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<String> lines = new ArrayList<>();
    lines.add("**Laning stage**");
    for (Pair pair : pairs) {
      lines.add(
          line(
              pair,
              assignments.getOrDefault(pair.pairId(), List.of()),
              reasons.getOrDefault(pair.pairId(), List.of()),
              heroes,
              yours));
    }
    return lines;
  }

  /**
   * One pair per lane, the longest lasting when a lane has several, in bottom, mid, top order; rows
   * that cannot be read are out.
   */
  private List<Pair> pairs(long matchId) {
    Map<Integer, Pair> byPair = new HashMap<>();
    for (LaneResultDomain row : resultRepo.findByMatchId(matchId)) {
      TeamSide side = Enums.find(TeamSide.class, row.getTeam());
      Zone.Lane lane = Enums.find(Zone.Lane.class, row.getLane());
      if (row.getPairId() == null || side == null || lane == null) {
        continue;
      }
      byPair
          .computeIfAbsent(row.getPairId(), id -> new Pair(lane, id, new EnumMap<>(TeamSide.class)))
          .sides()
          .put(side, row);
    }
    Map<Zone.Lane, Pair> longest = new EnumMap<>(Zone.Lane.class);
    byPair.values().stream()
        .sorted(Comparator.comparingInt(Pair::pairId))
        .forEach(p -> longest.merge(p.lane(), p, LanesSection::longer));
    return new ArrayList<>(longest.values());
  }

  private static Pair longer(Pair a, Pair b) {
    return length(b) > length(a) ? b : a;
  }

  private static long length(Pair pair) {
    return pair.sides().values().stream()
        .filter(r -> r.getStartSeconds() != null && r.getEndSeconds() != null)
        .mapToLong(r -> (long) r.getEndSeconds() - r.getStartSeconds())
        .max()
        .orElse(-1);
  }

  private static LaneResultDomain any(Map<TeamSide, LaneResultDomain> sides) {
    return sides.values().iterator().next();
  }

  private String line(
      Pair pair,
      List<LaneAssignmentDomain> assignments,
      List<LaneReasonDomain> reasons,
      Map<Long, String> heroes,
      TeamSide yours) {
    LaneResultDomain first = any(pair.sides());
    StringBuilder head =
        new StringBuilder(LaneWording.capitalised(pair.lane().name().toLowerCase(Locale.ROOT)))
            .append(" lane");
    duration(first).ifPresent(d -> head.append(" (lasted ").append(d).append(")"));
    head.append(": ").append(verdict(pair.sides(), yours)).append(".");
    String heroLine = heroLine(pair, assignments, heroes);
    String fixed = heroLine.isEmpty() ? head.toString() : head + " " + heroLine;
    List<String> why = why(pair.sides(), reasons);
    String note = levelNote(reasons).map(n -> " " + n + ".").orElse("");
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxLaneLineLength());
    for (int count = why.size(); count >= 0; count--) {
      String line =
          fixed
              + (count == 0 ? "" : " Why: " + String.join("; ", why.subList(0, count)) + ".")
              + note;
      if (line.length() <= max) {
        return line;
      }
    }
    return LineText.cut(fixed + note, max);
  }

  private static Optional<String> duration(LaneResultDomain row) {
    if (row.getStartSeconds() == null || row.getEndSeconds() == null) {
      return Optional.empty();
    }
    return Optional.of(GameTime.clock(Math.max(0, row.getEndSeconds() - row.getStartSeconds())));
  }

  private String verdict(Map<TeamSide, LaneResultDomain> sides, TeamSide yours) {
    Winner winner = winner(sides);
    if (winner != null) {
      String how =
          winner.row().getMargin() == null
              ? ""
              : " "
                  + LaneWording.strength(
                      winner.row().getMargin(),
                      properties.getLaneClearMargin(),
                      properties.getLaneBigMargin());
      return Prettify.teamOf(winner.side().name(), yours) + " won" + how;
    }
    Map.Entry<TeamSide, LaneResultDomain> only = sides.entrySet().iterator().next();
    if (sides.size() == 1
        && Enums.find(LaneResult.class, only.getValue().getResult()) == LaneResult.UNCONTESTED) {
      return Prettify.team(only.getKey().name()) + " was alone";
    }
    return "even";
  }

  private static Winner winner(Map<TeamSide, LaneResultDomain> sides) {
    return sides.entrySet().stream()
        .filter(e -> Enums.find(LaneResult.class, e.getValue().getResult()) == LaneResult.WON)
        .map(e -> new Winner(e.getKey(), e.getValue()))
        .findFirst()
        .orElse(null);
  }

  private static String heroLine(
      Pair pair, List<LaneAssignmentDomain> assignments, Map<Long, String> heroes) {
    List<String> parts = new ArrayList<>();
    for (TeamSide side : pair.sides().keySet()) {
      List<String> names =
          assignments.stream()
              .filter(
                  a -> a.getPlayerSlot() != null && side == Enums.find(TeamSide.class, a.getTeam()))
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
   * What helped the winner and what hurt the loser, strongest first, at most {@link #MAX_REASONS}.
   * A fact both sides stored (same evidence, or same code about the same hero or the side) is said
   * once; the same code about different heroes is different facts. Nothing for an even lane.
   */
  private List<String> why(Map<TeamSide, LaneResultDomain> sides, List<LaneReasonDomain> reasons) {
    Winner winner = winner(sides);
    if (winner == null) {
      return List.of();
    }
    Set<String> seenFacts = new HashSet<>();
    Set<String> seenEvidence = new HashSet<>();
    return reasons.stream()
        .filter(r -> !LaneReason.NO_ULTIMATE_AT_LANE_END.equals(r.getCode()))
        .filter(r -> r.getEvidence() != null && !r.getEvidence().isBlank())
        .filter(r -> explainsResult(r, winner.side()))
        .sorted(
            Comparator.comparing(
                    (LaneReasonDomain r) -> r.getSeverity() == null ? 0 : r.getSeverity())
                .reversed())
        .filter(r -> seenFacts.add(r.getCode() + "/" + r.getSubjectSlot()))
        .map(r -> evidence(r.getEvidence()))
        .filter(e -> seenEvidence.add(e.toLowerCase(Locale.ROOT)))
        .limit(MAX_REASONS)
        .toList();
  }

  private static boolean explainsResult(LaneReasonDomain reason, TeamSide winner) {
    TeamSide side = Enums.find(TeamSide.class, reason.getTeam());
    if (side == null) {
      return false;
    }
    String direction = side == winner ? "HELPED" : "HURT";
    return direction.equals(reason.getDirection());
  }

  private static String evidence(String stored) {
    String text = stored.strip();
    return LaneWording.capitalised(
        text.endsWith(".") ? text.substring(0, text.length() - 1) : text);
  }

  private Optional<String> levelNote(List<LaneReasonDomain> reasons) {
    return reasons.stream()
        .filter(r -> LaneReason.NO_ULTIMATE_AT_LANE_END.equals(r.getCode()))
        .filter(r -> "HELPED".equals(r.getDirection()))
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
}
