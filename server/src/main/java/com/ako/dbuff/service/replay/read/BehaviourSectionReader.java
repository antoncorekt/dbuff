package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.dao.repo.TeamLaneSwapRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.dao.repo.TeamSplitStretchRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(80)
@RequiredArgsConstructor
class BehaviourSectionReader implements AnalysisSectionReader {
  private final TeamRotationRepo rotationRepo;
  private final TeamSplitStretchRepo stretchRepo;
  private final TeamLaneSwapRepo swapRepo;
  private final SmokeUseRepo smokeRepo;
  private final ObjectiveConversionRepo objectiveRepo;

  public record Behaviour(
      List<Rotation> rotations,
      List<SplitStretch> splitStretches,
      List<LaneSwap> laneSwaps,
      List<Smoke> smokes,
      List<ObjectiveConversion> objectiveConversions) {}

  public record Rotation(
      String team,
      Long slot,
      Integer startSeconds,
      Integer endSeconds,
      String endReason,
      String fromZone,
      String fromLane,
      String toZone,
      String toLane,
      String toSide,
      String groupZone,
      String groupLane,
      boolean teleported,
      String laneLeft,
      Integer kills,
      Integer creepKills,
      Integer fights,
      Integer fightsWon,
      Integer fightIndex,
      Integer proxyOwnKills,
      Integer proxyEnemyKills,
      String reasons,
      Integer incomeBefore,
      Integer incomeDuring,
      Integer xpBefore,
      Integer xpDuring,
      String verdict) {}

  public record SplitStretch(
      String team,
      Integer startSeconds,
      Integer endSeconds,
      String label,
      Integer towersLost,
      Integer towersTaken,
      Integer ownHeroesDied,
      Integer enemyHeroesDied) {}

  public record LaneSwap(
      String team,
      Long slot,
      String laneBefore,
      String laneAfter,
      Integer fromSeconds,
      Integer toSeconds) {}

  public record Smoke(
      String team,
      Long userSlot,
      String groupSlots,
      Integer startSeconds,
      Integer endSeconds,
      String endReason,
      String startZone,
      String startLane,
      String endZone,
      String endLane,
      String outcome,
      Integer kills,
      Integer buildings,
      Integer roshanKills,
      Integer groupDeaths,
      Integer enemyDeaths) {}

  public record ObjectiveConversion(
      Integer fightIndex,
      String winnerTeam,
      String result,
      String objectiveKind,
      String objectiveKey,
      Integer secondsAfter,
      String stateBefore,
      Integer winnersAlive,
      String nearestTower,
      String towerLane,
      Integer towerDistance,
      Long nearestHeroSlot,
      String heroZone,
      String heroLane,
      String roshanNearSlots,
      String roshanCall) {}

  @Override
  public String name() {
    return "behaviour";
  }

  @Override
  @Transactional(readOnly = true)
  public Behaviour read(long matchId) {
    return new Behaviour(
        Sorted.of(
                rotationRepo.findByMatchId(matchId),
                Sorted.by(
                    TeamRotationDomain::getStartSeconds,
                    TeamRotationDomain::getTeam,
                    TeamRotationDomain::getPlayerSlot))
            .stream()
            .map(BehaviourSectionReader::rotation)
            .toList(),
        Sorted.of(
                stretchRepo.findByMatchId(matchId),
                Sorted.by(TeamSplitStretchDomain::getStartSeconds, TeamSplitStretchDomain::getTeam))
            .stream()
            .map(BehaviourSectionReader::stretch)
            .toList(),
        Sorted.of(
                swapRepo.findByMatchId(matchId),
                Sorted.by(
                    TeamLaneSwapDomain::getFromSeconds,
                    TeamLaneSwapDomain::getTeam,
                    TeamLaneSwapDomain::getPlayerSlot))
            .stream()
            .map(BehaviourSectionReader::swap)
            .toList(),
        Sorted.of(
                smokeRepo.findByMatchId(matchId),
                Sorted.by(SmokeUseDomain::getStartSeconds, SmokeUseDomain::getTeam))
            .stream()
            .map(BehaviourSectionReader::smoke)
            .toList(),
        Sorted.of(
                objectiveRepo.findByMatchId(matchId),
                Sorted.by(
                    ObjectiveConversionDomain::getFightIndex,
                    ObjectiveConversionDomain::getSecondsAfter))
            .stream()
            .map(BehaviourSectionReader::objective)
            .toList());
  }

  private static Rotation rotation(TeamRotationDomain r) {
    return new Rotation(
        r.getTeam(),
        r.getPlayerSlot(),
        r.getStartSeconds(),
        r.getEndSeconds(),
        r.getEndReason(),
        r.getFromZone(),
        r.getFromLane(),
        r.getToZone(),
        r.getToLane(),
        r.getToSide(),
        r.getGroupZone(),
        r.getGroupLane(),
        r.isTeleported(),
        r.getLaneLeft(),
        r.getKills(),
        r.getCreepKills(),
        r.getFights(),
        r.getFightsWon(),
        r.getFightIndex(),
        r.getProxyOwnKills(),
        r.getProxyEnemyKills(),
        r.getReasons(),
        r.getIncomeBefore(),
        r.getIncomeDuring(),
        r.getXpBefore(),
        r.getXpDuring(),
        r.getVerdict());
  }

  private static SplitStretch stretch(TeamSplitStretchDomain s) {
    return new SplitStretch(
        s.getTeam(),
        s.getStartSeconds(),
        s.getEndSeconds(),
        s.getLabel(),
        s.getTowersLost(),
        s.getTowersTaken(),
        s.getOwnHeroesDied(),
        s.getEnemyHeroesDied());
  }

  private static LaneSwap swap(TeamLaneSwapDomain s) {
    return new LaneSwap(
        s.getTeam(),
        s.getPlayerSlot(),
        s.getLaneBefore(),
        s.getLaneAfter(),
        s.getFromSeconds(),
        s.getToSeconds());
  }

  private static Smoke smoke(SmokeUseDomain s) {
    return new Smoke(
        s.getTeam(),
        s.getUserSlot(),
        s.getGroupSlots(),
        s.getStartSeconds(),
        s.getEndSeconds(),
        s.getEndReason(),
        s.getStartZone(),
        s.getStartLane(),
        s.getEndZone(),
        s.getEndLane(),
        s.getOutcome(),
        s.getKills(),
        s.getBuildings(),
        s.getRoshanKills(),
        s.getGroupDeaths(),
        s.getEnemyDeaths());
  }

  private static ObjectiveConversion objective(ObjectiveConversionDomain o) {
    return new ObjectiveConversion(
        o.getFightIndex(),
        o.getWinnerTeam(),
        o.getResult(),
        o.getObjectiveKind(),
        o.getObjectiveKey(),
        o.getSecondsAfter(),
        o.getStateBefore(),
        o.getWinnersAlive(),
        o.getNearestTower(),
        o.getTowerLane(),
        o.getTowerDistance(),
        o.getNearestHeroSlot(),
        o.getHeroZone(),
        o.getHeroLane(),
        o.getRoshanNearSlots(),
        o.getRoshanCall());
  }
}
