package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    List<TeamRotationDomain> rows = rotationRepo.findByMatchId(input.matchId());
    List<TeamRotationDomain> listed =
        rows == null
            ? List.of()
            : rows.stream()
                .filter(Objects::nonNull)
                .filter(r -> verdict(r) != null)
                .sorted(byWorth())
                .limit(properties.getMaxRotationLines())
                .sorted(CHRONOLOGICAL)
                .toList();
    if (listed.isEmpty()) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    int max = Math.max(LineText.ELLIPSIS.length() + 1, properties.getMaxBehaviourLineLength());
    List<String> lines = new ArrayList<>();
    lines.add("**Team rotations**");
    for (TeamRotationDomain row : listed) {
      lines.add(LineText.cut(line(row, heroes, yours), max));
    }
    return lines;
  }

  private static RotationVerdict verdict(TeamRotationDomain row) {
    RotationVerdict verdict = Enums.find(RotationVerdict.class, row.getVerdict());
    return verdict == null || !PRIORITY.containsKey(verdict) ? null : verdict;
  }

  private static Comparator<TeamRotationDomain> byWorth() {
    return Comparator.comparingInt((TeamRotationDomain r) -> PRIORITY.get(verdict(r)))
        .thenComparing(Comparator.comparingInt(RotationsSection::gain).reversed())
        .thenComparing(CHRONOLOGICAL);
  }

  private static int gain(TeamRotationDomain row) {
    return Objects.requireNonNullElse(row.getKills(), 0)
        + Objects.requireNonNullElse(row.getFightsWon(), 0);
  }

  private static String line(TeamRotationDomain row, Map<Long, String> heroes, TeamSide yours) {
    String hero = BehaviourWording.hero(heroes, row.getPlayerSlot());
    String why =
        BehaviourWording.reasons(row).stream()
            .map(reason -> BehaviourWording.rotationSentence(reason, hero, row, false))
            .collect(Collectors.joining("; "));
    return "%s %s (%s) went%s to %s: %s.%s"
        .formatted(
            row.getStartSeconds() == null ? "?" : GameTime.clock(row.getStartSeconds()),
            hero,
            BehaviourWording.teamTag(row.getTeam(), yours),
            row.getFromZone() == null
                ? ""
                : " from " + BehaviourWording.place(row.getFromZone(), row.getFromLane()),
            BehaviourWording.place(row.getToZone(), row.getToLane()),
            BehaviourWording.words(row.getVerdict()),
            why.isEmpty() ? "" : " Why: " + why + ".");
  }
}
