package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(30)
@RequiredArgsConstructor
class FightsSectionReader implements AnalysisSectionReader {
  static final int TOP_REASONS = 5;

  private final FightExplanationReader explanationReader;
  private final FightImportanceRepo importanceRepo;
  private final FightRepo fightRepo;

  /**
   * An important fight with the explanation of its loss when one is stored (those fields are null
   * otherwise).
   *
   * @param totalReasons every reason stored for the losing team
   * @param reasonsShown the ranked reasons in {@code reasons}: at most the top {@value
   *     #TOP_REASONS}, so a value below {@code totalReasons} means some were cut
   */
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
      int reasonsShown,
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
  @Transactional(readOnly = true)
  public List<ImportantFight> read(long matchId, RunStatus run) {
    List<FightImportanceDomain> important =
        importanceRepo.findByMatchId(matchId).stream()
            .filter(i -> i.isImportant() && i.getFightIndex() != null)
            .sorted(
                RowOrder.nullsLast(FightImportanceDomain::getFightIndex)
                    .thenComparing(RowOrder.nullsLast(FightImportanceDomain::getId)))
            .toList();
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getFightIndex() != null)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    Map<Integer, LostFight> lost =
        explanationReader.lostFights(
            matchId,
            important.stream().map(FightImportanceDomain::getFightIndex).toList(),
            TOP_REASONS);
    return important.stream()
        .filter(i -> fights.containsKey(i.getFightIndex()))
        .map(i -> fight(fights.get(i.getFightIndex()), i, lost.get(i.getFightIndex())))
        .toList();
  }

  private static ImportantFight fight(
      FightDomain f, FightImportanceDomain importance, LostFight lost) {
    FightExplanationDomain e = lost == null ? null : lost.explanation();
    List<Reason> shown =
        lost == null
            ? List.of()
            : lost.reasons().stream()
                .filter(RankedReason::ranked)
                .map(FightsSectionReader::reason)
                .toList();
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
        e == null ? null : e.getSpikeIndex(),
        e == null ? null : e.getLosingTeam(),
        e == null ? null : e.getGoldLost(),
        e == null ? null : e.getUnmatchedDeaths(),
        importance(importance),
        lost == null ? 0 : lost.totalReasons(),
        shown.size(),
        shown);
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
