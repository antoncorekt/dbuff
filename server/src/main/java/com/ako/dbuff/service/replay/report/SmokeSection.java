package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeOutcome;
import com.ako.dbuff.service.replay.analysis.behaviour.SmokeUse;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The Smoke of Deceit uses of the match, one line each: who used it and with whom, from when to
 * when, where the group went, what came of it and how the smoke ended. When there are more uses
 * than {@code max-smoke-lines}, those that paid off or backfired come before those that came to
 * nothing; the lines are listed in time order. A match with no smoke prints nothing. It reads
 * {@code smoke_use} only, so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class SmokeSection implements ReplayReportSection {
  private static final Comparator<SmokeUseDomain> CHRONOLOGICAL =
      Comparator.comparing(
              SmokeUseDomain::getStartSeconds, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              SmokeUseDomain::getUserSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(SmokeUseDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private static final Comparator<SmokeUseDomain> BY_WORTH =
      Comparator.comparingInt(SmokeSection::rank).thenComparing(CHRONOLOGICAL);

  private final SmokeUseRepo smokeRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "smoke";
  }

  @Override
  public int order() {
    return 72;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<SmokeUseDomain> rows = smokeRepo.findByMatchId(input.matchId());
    if (rows == null || rows.stream().allMatch(Objects::isNull)) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    return BehaviourLines.render(
        "**Smoke**",
        rows,
        BY_WORTH,
        CHRONOLOGICAL,
        properties.getMaxSmokeLines(),
        properties.getMaxBehaviourLineLength(),
        row -> line(row, heroes, yours));
  }

  private static int rank(SmokeUseDomain row) {
    SmokeOutcome outcome = Enums.find(SmokeOutcome.class, row.getOutcome());
    return outcome == null ? 2 : outcome == SmokeOutcome.NOTHING ? 1 : 0;
  }

  private static String line(SmokeUseDomain row, Map<Long, String> heroes, TeamSide yours) {
    String user = BehaviourWording.hero(heroes, row.getUserSlot());
    SmokeOutcome outcome = Enums.find(SmokeOutcome.class, row.getOutcome());
    SmokeUse.End end = Enums.find(SmokeUse.End.class, row.getEndReason());
    return "%s to %s %s (%s) used a Smoke of Deceit %s: %s; %s; %s."
        .formatted(
            time(row.getStartSeconds()),
            time(row.getEndSeconds()),
            user,
            BehaviourWording.teamTag(row.getTeam(), yours),
            BehaviourWording.smokeCompany(row, heroes),
            BehaviourWording.smokeWhere(row),
            outcome == null ? "outcome not known" : outcome.words(),
            end == null || end == SmokeUse.End.UNKNOWN
                ? "how it ended is not known"
                : "the smoke " + end.words());
  }

  private static String time(Integer seconds) {
    return seconds == null ? "?" : GameTime.clock(seconds);
  }
}
