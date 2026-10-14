package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(60)
@RequiredArgsConstructor
class LanesSectionReader implements AnalysisSectionReader {
  private final LaneAssignmentRepo assignmentRepo;
  private final LaneResultRepo resultRepo;
  private final LaneReasonRepo reasonRepo;

  public record Lanes(List<Assignment> assignments, List<Result> results) {}

  public record Assignment(
      Long slot,
      String team,
      String lane,
      Integer pairId,
      String position,
      Integer startSeconds,
      Integer endSeconds,
      String endReason) {}

  /**
   * @param slots the heroes of this side of the lane, the partners of one another
   */
  public record Result(
      Integer pairId,
      String lane,
      String team,
      String position,
      List<Long> slots,
      Integer heroes,
      Integer enemyHeroes,
      Integer startSeconds,
      Integer endSeconds,
      String endReason,
      Integer sideEndSeconds,
      String sideEndReason,
      Double creepScore,
      Integer lastHits,
      Integer siegeLastHits,
      Integer denies,
      Double xp,
      Double gold,
      Double level,
      Integer kills,
      Integer deaths,
      Long heroDamageDealt,
      Long heroDamageTaken,
      Long towerDamage,
      Boolean ownTowerFell,
      Boolean enemyTowerFell,
      String result,
      Double margin,
      List<Reason> reasons) {}

  public record Reason(
      Integer rank,
      String code,
      String direction,
      Long subjectSlot,
      Double severity,
      String evidence,
      String source) {}

  @Override
  public String name() {
    return "lanes";
  }

  @Override
  @Transactional(readOnly = true)
  public Lanes read(long matchId, RunStatus run) {
    List<LaneAssignmentDomain> assignments =
        assignmentRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(LaneAssignmentDomain::getPairId)
                    .thenComparing(RowOrder.nullsLast(LaneAssignmentDomain::getTeam))
                    .thenComparing(RowOrder.nullsLast(LaneAssignmentDomain::getStartSeconds))
                    .thenComparing(RowOrder.nullsLast(LaneAssignmentDomain::getPlayerSlot))
                    .thenComparing(RowOrder.nullsLast(LaneAssignmentDomain::getId)))
            .toList();
    List<LaneReasonDomain> reasons = reasonRepo.findByMatchId(matchId);
    return new Lanes(
        assignments.stream().map(LanesSectionReader::assignment).toList(),
        resultRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(LaneResultDomain::getPairId)
                    .thenComparing(RowOrder.nullsLast(LaneResultDomain::getTeam))
                    .thenComparing(RowOrder.nullsLast(LaneResultDomain::getId)))
            .map(r -> result(r, assignments, reasons))
            .toList());
  }

  private static Assignment assignment(LaneAssignmentDomain a) {
    return new Assignment(
        a.getPlayerSlot(),
        a.getTeam(),
        a.getLane(),
        a.getPairId(),
        a.getPosition(),
        a.getStartSeconds(),
        a.getEndSeconds(),
        a.getEndReason());
  }

  private static Result result(
      LaneResultDomain r, List<LaneAssignmentDomain> assignments, List<LaneReasonDomain> reasons) {
    List<Long> slots =
        assignments.stream()
            .filter(a -> Objects.equals(a.getPairId(), r.getPairId()))
            .filter(a -> Objects.equals(a.getTeam(), r.getTeam()))
            .map(LaneAssignmentDomain::getPlayerSlot)
            .distinct()
            .toList();
    List<Reason> own =
        reasons.stream()
            .filter(x -> Objects.equals(x.getPairId(), r.getPairId()))
            .filter(x -> Objects.equals(x.getTeam(), r.getTeam()))
            .sorted(
                RowOrder.nullsLast(LaneReasonDomain::getRank)
                    .thenComparing(RowOrder.nullsLast(LaneReasonDomain::getId)))
            .map(
                x ->
                    new Reason(
                        x.getRank(),
                        x.getCode(),
                        x.getDirection(),
                        x.getSubjectSlot(),
                        x.getSeverity(),
                        x.getEvidence(),
                        x.getSource()))
            .toList();
    return new Result(
        r.getPairId(),
        r.getLane(),
        r.getTeam(),
        r.getPosition(),
        slots,
        r.getHeroes(),
        r.getEnemyHeroes(),
        r.getStartSeconds(),
        r.getEndSeconds(),
        r.getEndReason(),
        r.getSideEndSeconds(),
        r.getSideEndReason(),
        r.getCreepScore(),
        r.getLastHits(),
        r.getSiegeLastHits(),
        r.getDenies(),
        r.getXp(),
        r.getGold(),
        r.getLevel(),
        r.getKills(),
        r.getDeaths(),
        r.getHeroDamageDealt(),
        r.getHeroDamageTaken(),
        r.getTowerDamage(),
        r.getOwnTowerFell(),
        r.getEnemyTowerFell(),
        r.getResult(),
        r.getMargin(),
        own);
  }
}
