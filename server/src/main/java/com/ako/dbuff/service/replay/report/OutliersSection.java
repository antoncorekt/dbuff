package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The players whose share of the team's net worth did not fit how much they took part, one line
 * each with the stored explanation (income change point, main gold source, fights joined against
 * the team median, and for a feeder the deaths and how many of his rotations were wasted or ended
 * in a death). Rows of the tracked players' team first, then the longest stretches, at most {@code
 * max-outlier-lines}, listed in time order. A row without an explanation (analysed before it
 * existed) is listed with the share only. It reads {@code player_outlier} and {@code team_rotation}
 * only, so it is independent of other sections.
 */
@Component
@RequiredArgsConstructor
public class OutliersSection implements ReplayReportSection {
  private static final Comparator<PlayerOutlierDomain> CHRONOLOGICAL =
      Comparator.comparing(
              PlayerOutlierDomain::getStartSeconds, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getPlayerSlot, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getKind, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              PlayerOutlierDomain::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private final PlayerOutlierRepo outlierRepo;
  private final TeamRotationRepo rotationRepo;
  private final ReportHeroNames heroNames;
  private final ReplayReportProperties properties;

  @Override
  public String name() {
    return "outliers";
  }

  @Override
  public int order() {
    return 78;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<PlayerOutlierDomain> rows = outlierRepo.findByMatchId(input.matchId());
    if (rows == null || rows.stream().noneMatch(OutliersSection::usable)) {
      return List.of();
    }
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    List<TeamRotationDomain> rotations = rotationRepo.findByMatchId(input.matchId());
    Comparator<PlayerOutlierDomain> byWorth =
        Comparator.comparingInt(
                (PlayerOutlierDomain o) -> TeamSide.ofSlot(o.getPlayerSlot()) == yours ? 0 : 1)
            .thenComparing(
                (PlayerOutlierDomain o) -> o.getEndSeconds() - o.getStartSeconds(),
                Comparator.reverseOrder())
            .thenComparing(CHRONOLOGICAL);
    return BehaviourLines.render(
        "**Net worth outliers**",
        rows.stream().filter(OutliersSection::usable).toList(),
        byWorth,
        CHRONOLOGICAL,
        properties.getMaxOutlierLines(),
        properties.getMaxBehaviourLineLength(),
        o -> line(o, heroes, yours, rotations));
  }

  private static boolean usable(PlayerOutlierDomain o) {
    return o != null
        && o.getPlayerSlot() != null
        && o.getStartSeconds() != null
        && o.getEndSeconds() != null
        && o.getSharePercent() != null
        && o.getKind() != null;
  }

  private static String line(
      PlayerOutlierDomain o,
      Map<Long, String> heroes,
      TeamSide yours,
      List<TeamRotationDomain> rotations) {
    String team = Prettify.teamOf(TeamSide.ofSlot(o.getPlayerSlot()).name(), yours);
    String hero = BehaviourWording.hero(heroes, o.getPlayerSlot());
    boolean feeder = "FEEDER".equals(o.getKind());
    String share =
        String.format(
            Locale.ROOT,
            "%s held %s%.0f%% of the net worth of %s from %s",
            hero,
            feeder ? "only " : "",
            o.getSharePercent(),
            team,
            GameTime.clock(o.getStartSeconds()));
    String why = o.getExplanation();
    String wasted = feeder ? wastedRotations(o, rotations) : null;
    if (why == null && wasted == null) {
      return share;
    }
    String inside = why == null ? wasted : wasted == null ? why : why + "; " + wasted;
    return share + " (" + inside + ")";
  }

  private static String wastedRotations(PlayerOutlierDomain o, List<TeamRotationDomain> rotations) {
    if (rotations == null) {
      return null;
    }
    List<TeamRotationDomain> his =
        rotations.stream()
            .filter(r -> r != null && o.getPlayerSlot().equals(r.getPlayerSlot()))
            .filter(r -> r.getStartSeconds() != null)
            .filter(
                r ->
                    r.getStartSeconds() >= o.getStartSeconds()
                        && r.getStartSeconds() <= o.getEndSeconds() + 60)
            .toList();
    long bad =
        his.stream()
            .filter(
                r ->
                    RotationVerdict.WASTED.name().equals(r.getVerdict())
                        || RotationVerdict.DIED.name().equals(r.getVerdict()))
            .count();
    return his.isEmpty()
        ? null
        : bad + " of his " + his.size() + " rotations were wasted or ended in a death";
  }
}
