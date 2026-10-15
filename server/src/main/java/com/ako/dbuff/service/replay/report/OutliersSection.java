package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.outlier.OutlierRowOrder;
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
      OutlierRowOrder.CHRONOLOGICAL;

  /** An outlier stretch lasts to the end of its last minute. */
  private static final int LAST_MINUTE_SECONDS = 60;

  /** Reads the rotations the first time a line needs them. */
  private final class LazyRotations {
    private final long matchId;
    private List<TeamRotationDomain> loaded;

    private LazyRotations(long matchId) {
      this.matchId = matchId;
    }

    private List<TeamRotationDomain> get() {
      if (loaded == null) {
        loaded = rotationRepo.findByMatchId(matchId);
      }
      return loaded;
    }
  }

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
    Map<Long, String> heroes = heroNames.iconBySlot(input.matchId());
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    LazyRotations rotations = new LazyRotations(input.matchId());
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

  private String line(
      PlayerOutlierDomain o, Map<Long, String> heroes, TeamSide yours, LazyRotations rotations) {
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
    String wasted = feeder ? wastedRotations(o, rotations.get()) : null;
    if (why == null && wasted == null) {
      return share;
    }
    String inside = why == null ? wasted : wasted == null ? why : why + "; " + wasted;
    return share + " (" + inside + ")";
  }

  private String wastedRotations(PlayerOutlierDomain o, List<TeamRotationDomain> rotations) {
    if (rotations == null) {
      return null;
    }
    List<TeamRotationDomain> his =
        rotations.stream()
            .filter(r -> r != null && o.getPlayerSlot().equals(r.getPlayerSlot()))
            .filter(r -> r.getStartSeconds() != null)
            .filter(
                r ->
                    r.getStartSeconds() <= o.getEndSeconds() + LAST_MINUTE_SECONDS
                        && (r.getEndSeconds() == null ? r.getStartSeconds() : r.getEndSeconds())
                            >= o.getStartSeconds())
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
