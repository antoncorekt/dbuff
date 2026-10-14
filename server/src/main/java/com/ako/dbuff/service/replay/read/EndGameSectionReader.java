package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(90)
@RequiredArgsConstructor
class EndGameSectionReader implements AnalysisSectionReader {
  private final EndGameSummaryRepo summaryRepo;

  public record EndGame(
      String winnerTeam,
      Integer durationSeconds,
      Boolean leadKnown,
      Integer peakLeadSeconds,
      Long peakLeadGold,
      Double peakLeadPercent,
      Boolean leadLost,
      Long lastLeadGold,
      Integer heldSeconds,
      Boolean thrown,
      Integer turningFightIndex,
      Double turningSeconds,
      String turningKind,
      String objectiveKind,
      String objectiveKey,
      Integer objectiveSecondsAfter,
      Long turningGoldChange,
      Integer radiantBuybacks,
      Integer direBuybacks,
      Integer buybackWindowSeconds,
      Integer finalFightIndex,
      Double finalFightSeconds,
      String finalFightWinner) {}

  @Override
  public String name() {
    return "endgame";
  }

  @Override
  public List<EndGame> read(long matchId, RunStatus run) {
    return summaryRepo.findByMatchId(matchId).stream()
        .sorted(RowOrder.nullsLast(EndGameSummaryDomain::getId))
        .map(
            s ->
                new EndGame(
                    s.getWinnerTeam(),
                    s.getDurationSeconds(),
                    s.getLeadKnown(),
                    s.getPeakLeadSeconds(),
                    s.getPeakLeadGold(),
                    s.getPeakLeadPercent(),
                    s.getLeadLost(),
                    s.getLastLeadGold(),
                    s.getHeldSeconds(),
                    s.getThrown(),
                    s.getTurningFightIndex(),
                    s.getTurningSeconds(),
                    s.getTurningKind(),
                    s.getObjectiveKind(),
                    s.getObjectiveKey(),
                    s.getObjectiveSecondsAfter(),
                    s.getTurningGoldChange(),
                    s.getRadiantBuybacks(),
                    s.getDireBuybacks(),
                    s.getBuybackWindowSeconds(),
                    s.getFinalFightIndex(),
                    s.getFinalFightSeconds(),
                    s.getFinalFightWinner()))
        .toList();
  }
}
