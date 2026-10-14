package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.PlayerOutlierDomain;
import com.ako.dbuff.dao.repo.PlayerOutlierRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(75)
@RequiredArgsConstructor
class OutliersSectionReader implements AnalysisSectionReader {
  private final PlayerOutlierRepo outlierRepo;

  /**
   * The explanation columns are null on rows written before analysis version 2, until the match is
   * analysed again.
   */
  public record Outlier(
      Long slot,
      String kind,
      Integer startSeconds,
      Integer endSeconds,
      Double sharePercent,
      Integer participationRank,
      Integer fightsJoined,
      Integer deaths,
      Integer incomeBreakSeconds,
      String mainIncomeSource,
      String explanation) {}

  @Override
  public String name() {
    return "outliers";
  }

  @Override
  public List<Outlier> read(long matchId, RunStatus run) {
    return outlierRepo.findByMatchId(matchId).stream()
        .sorted(
            RowOrder.nullsLast(PlayerOutlierDomain::getPlayerSlot)
                .thenComparing(RowOrder.nullsLast(PlayerOutlierDomain::getStartSeconds))
                .thenComparing(RowOrder.nullsLast(PlayerOutlierDomain::getKind))
                .thenComparing(RowOrder.nullsLast(PlayerOutlierDomain::getId)))
        .map(
            o ->
                new Outlier(
                    o.getPlayerSlot(),
                    o.getKind(),
                    o.getStartSeconds(),
                    o.getEndSeconds(),
                    o.getSharePercent(),
                    o.getParticipationRank(),
                    o.getFightsJoined(),
                    o.getDeaths(),
                    o.getIncomeBreakSeconds(),
                    o.getMainIncomeSource(),
                    o.getExplanation()))
        .toList();
  }
}
