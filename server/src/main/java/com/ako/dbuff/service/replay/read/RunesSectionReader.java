package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(50)
@RequiredArgsConstructor
class RunesSectionReader implements AnalysisSectionReader {
  private final RunePickupRepo pickupRepo;
  private final OpeningSummaryRepo openingRepo;

  public record Runes(List<Pickup> pickups, List<Opening> openings) {}

  public record Pickup(
      Long slot,
      String team,
      Integer runeIndex,
      String runeType,
      Integer seconds,
      String zone,
      String lane,
      Integer gold,
      Double effectEndSeconds,
      Boolean midLane) {}

  public record Opening(
      String team,
      Integer windowStart,
      Integer windowEnd,
      Integer runesTaken,
      Integer bountyGold,
      Integer bountyGoldGame,
      Long firstTakerSlot,
      Integer firstTakerSeconds,
      Integer kills,
      Long gold,
      Long xp,
      Long balance,
      String result) {}

  @Override
  public String name() {
    return "runes";
  }

  @Override
  @Transactional(readOnly = true)
  public Runes read(long matchId, RunStatus run) {
    return new Runes(
        pickupRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(RunePickupDomain::getSeconds)
                    .thenComparing(RowOrder.nullsLast(RunePickupDomain::getRuneIndex))
                    .thenComparing(RowOrder.nullsLast(RunePickupDomain::getPlayerSlot))
                    .thenComparing(RowOrder.nullsLast(RunePickupDomain::getId)))
            .map(
                p ->
                    new Pickup(
                        p.getPlayerSlot(),
                        p.getTeam(),
                        p.getRuneIndex(),
                        p.getRuneType(),
                        p.getSeconds(),
                        p.getZone(),
                        p.getLane(),
                        p.getGold(),
                        p.getEffectEndSeconds(),
                        p.getMidLane()))
            .toList(),
        openingRepo.findByMatchId(matchId).stream()
            .sorted(
                RowOrder.nullsLast(OpeningSummaryDomain::getTeam)
                    .thenComparing(RowOrder.nullsLast(OpeningSummaryDomain::getId)))
            .map(
                o ->
                    new Opening(
                        o.getTeam(),
                        o.getWindowStart(),
                        o.getWindowEnd(),
                        o.getRunesTaken(),
                        o.getBountyGold(),
                        o.getBountyGoldGame(),
                        o.getFirstTakerSlot(),
                        o.getFirstTakerSeconds(),
                        o.getKills(),
                        o.getGold(),
                        o.getXp(),
                        o.getBalance(),
                        o.getResult()))
            .toList());
  }
}
