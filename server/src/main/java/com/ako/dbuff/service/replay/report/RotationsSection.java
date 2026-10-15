package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The team rotations worth reading, one line each: a hero who left his team's group for another
 * lane and either died, did nothing useful, or won something there. Deaths come first, then wasted
 * trips, then the good ones that came to most, up to {@code max-rotation-lines}; the lines are
 * listed in time order. Neutral trips are not listed. It reads {@code team_rotation} only, so it is
 * independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class RotationsSection implements ReplayReportSection {
  private static final Map<RotationVerdict, Integer> PRIORITY =
      Map.of(RotationVerdict.DIED, 0, RotationVerdict.WASTED, 1, RotationVerdict.GOOD, 2);

  private static final Comparator<TeamRotationDomain> CHRONOLOGICAL =
      Comparator.comparing(
              TeamRotationDomain::getStartSeconds, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              TeamRotationDomain::getPlayerSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              TeamRotationDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private final TeamRotationRepo rotationRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "rotations";
  }

  @Override
  public int order() {
    return 70;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return render(input, usableRows(input));
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<TeamRotationDomain> rows = usableRows(input);
    return SectionViews.titled(render(input, rows), tripsVerdict(rows, input));
  }

  private List<TeamRotationDomain> usableRows(ReplayReportInput input) {
    List<TeamRotationDomain> rows = rotationRepo.findByMatchId(input.matchId());
    return rows == null
        ? List.of()
        : rows.stream().filter(r -> r != null && verdict(r) != null).toList();
  }

  private static Verdict tripsVerdict(List<TeamRotationDomain> rows, ReplayReportInput input) {
    SectionViews.Scope<TeamRotationDomain> scope =
        SectionViews.scope(
            rows, TeamRotationDomain::getTeam, TeamSide.commonSide(input.focusSlots()));
    long good = scope.rows().stream().filter(r -> verdict(r) == RotationVerdict.GOOD).count();
    long bad = scope.rows().size() - good;
    return new Verdict(
        "%s: %d good, %d wasted or ended in a death"
            .formatted(scope.ownTeam() ? "Your team's trips" : "Trips of both teams", good, bad),
        scope.ownTeam() ? SectionViews.balance(good, bad) : Accent.NEUTRAL);
  }

  private List<String> render(ReplayReportInput input, List<TeamRotationDomain> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    return BehaviourLines.render(
        "**Team rotations**",
        rows,
        byWorth(),
        CHRONOLOGICAL,
        properties.getMaxRotationLines(),
        properties.getMaxBehaviourLineLength(),
        row -> line(row, heroes, yours));
  }

  private static RotationVerdict verdict(TeamRotationDomain row) {
    RotationVerdict verdict = Enums.find(RotationVerdict.class, row.getVerdict());
    return verdict == null || !PRIORITY.containsKey(verdict) ? null : verdict;
  }

  private static Comparator<TeamRotationDomain> byWorth() {
    return Comparator.comparingInt((TeamRotationDomain r) -> PRIORITY.get(verdict(r)))
        .thenComparingInt(r -> verdict(r) == RotationVerdict.GOOD ? -gain(r) : 0)
        .thenComparing(CHRONOLOGICAL);
  }

  private static int gain(TeamRotationDomain row) {
    return Objects.requireNonNullElse(row.getKills(), 0)
        + Objects.requireNonNullElse(row.getFightsWon(), 0);
  }

  private static String line(TeamRotationDomain row, Map<Long, String> heroes, TeamSide yours) {
    String hero = BehaviourWording.hero(heroes, row.getPlayerSlot());
    List<String> parts = new ArrayList<>();
    if (verdict(row) == RotationVerdict.GOOD) {
      String won = BehaviourWording.tripWon(row.getKills(), row.getFightsWon());
      if (!won.isEmpty()) {
        parts.add(LaneWording.capitalised(won) + ".");
      }
    }
    String why =
        BehaviourWording.reasons(row).stream()
            .map(reason -> BehaviourWording.rotationSentence(reason, hero, row, false))
            .collect(Collectors.joining("; "));
    if (!why.isEmpty()) {
      parts.add("Why: " + why + ".");
    }
    if (parts.isEmpty()) {
      parts.add("No reason recorded.");
    }
    return "%s %s (%s) went%s to %s: %s. %s"
        .formatted(
            row.getStartSeconds() == null ? "?" : GameTime.clock(row.getStartSeconds()),
            hero,
            BehaviourWording.teamTag(row.getTeam(), yours),
            row.getFromZone() == null
                ? ""
                : " from " + BehaviourWording.place(row.getFromZone(), row.getFromLane()),
            BehaviourWording.place(row.getToZone(), row.getToLane()),
            BehaviourWording.words(row.getVerdict()),
            String.join(" ", parts));
  }
}
