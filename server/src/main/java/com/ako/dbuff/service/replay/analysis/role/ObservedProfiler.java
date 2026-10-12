package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The profile of what each player did, from what the match stored for the role analyzer: the net
 * worth place in the team, OpenDota's lane role and the laning-phase activity. The functions that
 * need data not stored yet (vision duty from wards and dust, initiation from the front-line rank at
 * fights, disable landed, healing and saves, damage and damage taken) stay unknown rather than
 * guessed; their sources are the statistics and fight tables the design names, to be added with the
 * analyzers that read them.
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
    return java.util.stream.IntStream.range(0, team.size())
        .mapToObj(
            i -> {
              RolePlayer player = team.get(i);
              boolean seen = !player.activity().isEmpty();
              LaningActivity laning = LaningActivity.of(player.activity(), laningMinute * 60);
              double farmRank = NetWorthRanks.farmRank(finalRank[i], laningRank[i], Double.NaN);
              return new FunctionProfile(
                  lane(player, laning, rules),
                  Double.isNaN(farmRank) ? null : (team.size() - farmRank) / (team.size() - 1),
                  jungle(player, laning, seen, rules),
                  seen ? clamp(laning.roamShare() / rules.getRoamFullShare()) : null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null);
            })
        .toList();
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
    return Math.max(0, Math.min(1, value));
  }
}
