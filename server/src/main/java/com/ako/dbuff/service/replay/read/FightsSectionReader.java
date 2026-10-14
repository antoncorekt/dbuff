package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@RequiredArgsConstructor
class FightsSectionReader implements AnalysisSectionReader {
  private static final int TOP_REASONS = 5;

  private final FightExplanationReader explanationReader;

  public record ImportantFight(
      Integer fightIndex,
      String type,
      Double startSeconds,
      Double endSeconds,
      String zone,
      String lane,
      String winner,
      Integer radiantDeaths,
      Integer direDeaths,
      Integer spikeIndex,
      String losingTeam,
      Long goldLost,
      Integer unmatchedDeaths,
      Importance importance,
      int totalReasons,
      List<Reason> reasons) {}

  public record Importance(
      String winningTeam,
      Long goldChange,
      Long xpChange,
      Long netWorth,
      Double sharePercent,
      Boolean leadFlip,
      Boolean flipToWinner,
      Boolean slopeBreak) {}

  public record Reason(
      int rank,
      Long subjectSlot,
      String team,
      String scope,
      String code,
      Double severity,
      String evidence,
      List<String> also) {}

  @Override
  public String name() {
    return "fights";
  }

  @Override
  public List<ImportantFight> read(long matchId) {
    return explanationReader.importantFights(matchId, TOP_REASONS).stream()
        .map(FightsSectionReader::fight)
        .toList();
  }

  private static ImportantFight fight(LostFight lost) {
    FightDomain f = lost.fight();
    FightExplanationDomain e = lost.explanation();
    return new ImportantFight(
        f.getFightIndex(),
        f.getType(),
        f.getStartSeconds(),
        f.getEndSeconds(),
        f.getZone(),
        f.getLane(),
        f.getWinner(),
        f.getRadiantDeaths(),
        f.getDireDeaths(),
        e.getSpikeIndex(),
        e.getLosingTeam(),
        e.getGoldLost(),
        e.getUnmatchedDeaths(),
        importance(lost.importance()),
        lost.totalReasons(),
        lost.reasons().stream()
            .filter(RankedReason::ranked)
            .map(FightsSectionReader::reason)
            .toList());
  }

  private static Importance importance(FightImportanceDomain i) {
    return new Importance(
        i.getWinningTeam(),
        i.getGoldChange(),
        i.getXpChange(),
        i.getNetWorth(),
        i.getSharePercent(),
        i.getLeadFlip(),
        i.getFlipToWinner(),
        i.getSlopeBreak());
  }

  private static Reason reason(RankedReason r) {
    return new Reason(
        r.rank(),
        r.reason().getSubjectSlot(),
        r.reason().getTeam(),
        r.reason().getScope(),
        r.reason().getCode(),
        r.reason().getSeverity(),
        r.reason().getEvidence(),
        r.also());
  }
}
