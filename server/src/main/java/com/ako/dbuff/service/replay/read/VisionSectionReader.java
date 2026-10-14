package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
@RequiredArgsConstructor
class VisionSectionReader implements AnalysisSectionReader {
  private final FightVisionRepo visionRepo;

  public record FightVision(
      Integer fightIndex,
      String team,
      boolean lost,
      Double windowStart,
      Double windowEnd,
      Integer activeObservers,
      Integer activeSentries,
      Integer youngestObserverAge,
      Integer oldestObserverAge,
      Integer nearestWardDistance,
      Double visionWeight,
      boolean lateWard,
      Integer lateWardSeconds,
      Integer wardsDestroyed,
      String destroyedBy,
      boolean invisibleHero,
      String invisibleHeroes,
      boolean detection,
      boolean detectionUsed,
      String verdict) {}

  @Override
  public String name() {
    return "vision";
  }

  @Override
  public List<FightVision> read(long matchId) {
    return Sorted.of(
            visionRepo.findByMatchId(matchId),
            Sorted.by(FightVisionDomain::getFightIndex, FightVisionDomain::getTeam))
        .stream()
        .map(
            v ->
                new FightVision(
                    v.getFightIndex(),
                    v.getTeam(),
                    v.isLost(),
                    v.getWindowStart(),
                    v.getWindowEnd(),
                    v.getActiveObservers(),
                    v.getActiveSentries(),
                    v.getYoungestObserverAge(),
                    v.getOldestObserverAge(),
                    v.getNearestWardDistance(),
                    v.getVisionWeight(),
                    v.isLateWard(),
                    v.getLateWardSeconds(),
                    v.getWardsDestroyed(),
                    v.getDestroyedBy(),
                    v.isInvisibleHero(),
                    v.getInvisibleHeroes(),
                    v.isDetection(),
                    v.isDetectionUsed(),
                    v.getVerdict()))
        .toList();
  }
}
