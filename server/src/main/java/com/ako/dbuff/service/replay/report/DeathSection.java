package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Why the channel's tracked players died, one line per death worth explaining. */
@Component
@RequiredArgsConstructor
public class DeathSection implements ReplayReportSection {
  /** What most deserves a mention comes first; a reason not listed is not worth a line. */
  private static final Map<String, Integer> SEVERITY =
      Map.of(
          "BKB_READY_UNUSED", 1,
          "BKB_ON_COOLDOWN", 2,
          "BKB_EXPIRED", 3,
          "BKB_PIERCED", 4,
          "DISABLE_CHAIN", 5,
          "CAUGHT_OUT", 6,
          "OUT_OF_POSITION", 7,
          "BURST", 8,
          "TOWER", 9);

  private final DeathAnalysisRepo deathRepo;

  @Override
  public String name() {
    return "deaths";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public int order() {
    return 50;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    List<String> lines = new ArrayList<>();
    List<DeathAnalysisDomain> deaths = deathRepo.findByMatchId(input.matchId());
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      deaths.stream()
          .filter(
              d -> d.getPlayerSlot().equals(player.getKey()) && SEVERITY.containsKey(d.getReason()))
          .sorted(
              Comparator.comparingInt((DeathAnalysisDomain d) -> SEVERITY.get(d.getReason()))
                  .thenComparing(DeathAnalysisDomain::getDeathSeconds))
          .limit(input.maxFindings())
          .sorted(Comparator.comparing(DeathAnalysisDomain::getDeathSeconds))
          .map(d -> line(player.getValue(), d))
          .forEach(lines::add);
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    lines.addFirst("**Deaths**");
    return lines;
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<String> lines = lines(input);
    long deaths = Math.max(0, lines.size() - 1);
    return SectionViews.titled(
        lines, new Verdict(SectionViews.count(deaths, "death") + " worth a look", Accent.LOSS));
  }

  private static String line(String name, DeathAnalysisDomain d) {
    String killer = Prettify.unit(d.getKillerName());
    return "%s %s died%s: %s"
        .formatted(
            GameTime.clock(d.getDeathSeconds()),
            name,
            killer.isEmpty() ? "" : " to " + killer,
            reason(d));
  }

  private static String reason(DeathAnalysisDomain d) {
    return switch (d.getReason()) {
      case "BKB_READY_UNUSED" -> "had Black King Bar ready and did not use it";
      case "BKB_ON_COOLDOWN" -> "Black King Bar was on cooldown";
      case "BKB_EXPIRED" -> "Black King Bar had just run out";
      case "BKB_PIERCED" -> "a disable went through Black King Bar";
      case "DISABLE_CHAIN" ->
          String.format(
              Locale.ROOT, "disabled for %.1f s in the last seconds", d.getDisableSeconds());
      case "CAUGHT_OUT" -> "caught alone by two or more enemies";
      case "OUT_OF_POSITION" -> "stood well in front of where his range should put him";
      case "BURST" -> d.getDamageLastThreeSeconds() + " damage in 3 seconds";
      case "TOWER" -> "killed by a tower";
      default -> "";
    };
  }
}
