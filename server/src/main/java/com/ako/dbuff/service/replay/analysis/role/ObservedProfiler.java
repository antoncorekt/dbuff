package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The profile of what each player did, from what the match stored for the role analyzer: the net
 * worth place in the team, OpenDota's lane role and the laning-phase activity. The functions that
 * need data not stored yet (initiation from the front-line rank at fights, disable landed) stay
 * unknown rather than guessed. Damage, healing, durability from the damage taken, vision duty from
 * the wards placed and the share of last hits come from OpenDota's statistics, as shares of the
 * team total.
 */
@Component
@RequiredArgsConstructor
public class ObservedProfiler implements StageProfiler {
  private static final long SAFE_LANE = 1;
  private static final long MID_LANE = 2;
  private static final long OFF_LANE = 3;
  private static final long JUNGLE_LANE = 4;

  private final ReplayAnalysisProperties analysis;
  private final RoleProfileProperties properties;

  @Override
  public RoleStage stage() {
    return RoleStage.FINAL;
  }

  @Override
  public List<FunctionProfile> profile(List<RolePlayer> team) {
    RoleProfileProperties.Observed rules = properties.getObserved();
    int laningMinute = analysis.getPhaseLaneEndMinutes();
    double[] finalRank = NetWorthRanks.of(team.stream().map(RolePlayer::finalWorth).toList());
    double[] laningRank =
        NetWorthRanks.of(team.stream().map(p -> p.worthAt(laningMinute)).toList());
    List<Double> damage = teamShares(team, PlayerOutput::heroDamage, rules.getDamageFullShare());
    List<Double> healing = teamShares(team, PlayerOutput::heroHealing, rules.getHealFullShare());
    List<Double> taken =
        teamShares(team, PlayerOutput::damageTaken, rules.getDamageTakenFullShare());
    List<Double> wards = teamShares(team, PlayerOutput::wardsPlaced, rules.getWardFullShare());
    List<Double> lastHits = teamShares(team, PlayerOutput::lastHits, rules.getLastHitFullShare());
    return java.util.stream.IntStream.range(0, team.size())
        .mapToObj(
            i -> {
              RolePlayer player = team.get(i);
              boolean seen = !player.activity().isEmpty();
              LaningActivity laning = LaningActivity.of(player.activity(), laningMinute * 60);
              double farmRank = NetWorthRanks.farmRank(finalRank[i], laningRank[i], Double.NaN);
              return new FunctionProfile(
                  lane(player, laning, rules),
                  farm(farmRank, team.size(), lastHits.get(i), rules),
                  jungle(player, laning, seen, rules),
                  seen ? clamp(laning.roamShare() / rules.getRoamFullShare()) : null,
                  wards.get(i),
                  null,
                  null,
                  healing.get(i),
                  damage.get(i),
                  taken.get(i));
            })
        .toList();
  }

  private static Double farm(
      double farmRank, int size, Double lastHitShare, RoleProfileProperties.Observed rules) {
    Double byWorth = Double.isNaN(farmRank) || size < 2 ? null : (size - farmRank) / (size - 1);
    if (byWorth == null) {
      return lastHitShare;
    }
    if (lastHitShare == null) {
      return byWorth;
    }
    return (1 - rules.getLastHitFarmShare()) * byWorth + rules.getLastHitFarmShare() * lastHitShare;
  }

  /**
   * The value of each player as his share of the team total over {@code fullShare}, cut at 1. When
   * any player has no stored amount the team total is incomplete, so all are null; a total of 0
   * gives 0 to everyone.
   */
  private static List<Double> teamShares(
      List<RolePlayer> team, Function<PlayerOutput, Long> reader, double fullShare) {
    List<Long> amounts = team.stream().map(p -> reader.apply(p.output())).toList();
    if (amounts.stream().anyMatch(Objects::isNull)) {
      return amounts.stream().map(a -> (Double) null).toList();
    }
    long total = amounts.stream().mapToLong(Long::longValue).sum();
    return amounts.stream().map(a -> share(a, total, fullShare)).toList();
  }

  private static Double share(long amount, long total, double fullShare) {
    return total <= 0 ? Double.valueOf(0) : clamp(amount / (double) total / fullShare);
  }

  private static LanePosition lane(
      RolePlayer player, LaningActivity laning, RoleProfileProperties.Observed rules) {
    if (laning.midShare() >= rules.getMidLaneShare()) {
      return LanePosition.MID;
    }
    Long laneRole = player.laneRole();
    if (laneRole == null) {
      return null;
    }
    if (laneRole == SAFE_LANE) {
      return LanePosition.SAFE;
    }
    if (laneRole == MID_LANE) {
      return LanePosition.MID;
    }
    return laneRole == OFF_LANE ? LanePosition.OFF : null;
  }

  private static Double jungle(
      RolePlayer player,
      LaningActivity laning,
      boolean seen,
      RoleProfileProperties.Observed rules) {
    Double fromLane =
        player.laneRole() != null && player.laneRole() == JUNGLE_LANE
            ? rules.getJungleLaneRoleValue()
            : null;
    if (!seen) {
      return fromLane;
    }
    double fromActivity = clamp(laning.jungleShare() / rules.getJungleFullShare());
    return fromLane == null ? fromActivity : Math.max(fromLane, fromActivity);
  }

  private static double clamp(double value) {
    return Double.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
  }
}
