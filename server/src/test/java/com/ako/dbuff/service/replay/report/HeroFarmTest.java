package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeroFarmTest {
  private static CreepKillDomain kill(
      String creepClass, Double seconds, Integer gold, Boolean denied) {
    return CreepKillDomain.builder()
        .creepClass(creepClass)
        .seconds(seconds)
        .gold(gold)
        .denied(denied)
        .build();
  }

  private static PlayerActivitySegmentDomain jungle(int start, int end) {
    return PlayerActivitySegmentDomain.builder()
        .activity("JUNGLE_FARM")
        .startSeconds(start)
        .endSeconds(end)
        .build();
  }

  @Test
  void sumsLaneAndCampGoldPerMinuteAndSkipsDeniesAndBadRows() {
    HeroFarm farm =
        HeroFarm.of(
            1,
            List.of(
                kill("LANE_MELEE", 10.0, 40, false),
                kill("LANE_RANGED", 50.0, 50, false),
                kill("NEUTRAL", 70.0, 60, false),
                kill("NEUTRAL_ANCIENT", -20.0, 100, null),
                kill("LANE_MELEE", 80.0, 40, true),
                kill("LANE_MELEE", null, 40, false),
                kill("LANE_MELEE", Double.NaN, 40, false),
                kill("LANE_MELEE", 1.0e9, 40, false),
                kill("NOT_A_CLASS", 30.0, 40, false),
                kill(null, 30.0, 40, false),
                kill("LANE_MELEE", 90.0, null, false)));

    assertThat(farm.lane()).containsExactly(90, 0);
    assertThat(farm.neutral()).containsExactly(100, 60);
    assertThat(farm.mix()).isEqualTo(HeroFarm.Mix.JUNGLE);
  }

  @Test
  void noUsableRowGivesAnEmptyFarmWithoutFailing() {
    assertThat(HeroFarm.of(1, List.of()).totalGold()).isZero();
    assertThat(HeroFarm.of(1, List.of(kill("LANE_MELEE", null, 5, false))).lane()).isEmpty();
  }

  @Test
  void findsWhereTheJungleStartedAndMovesItBackToTheSegmentUnderWay() {
    java.util.ArrayList<CreepKillDomain> kills = new java.util.ArrayList<>();
    for (int m = 0; m < 12; m++) {
      kills.add(kill("LANE_MELEE", m * 60.0, 100, false));
    }
    for (int m = 12; m < 20; m++) {
      kills.add(kill("NEUTRAL", m * 60.0, 300, false));
    }
    HeroFarm farm = HeroFarm.of(1, kills);

    assertThat(farm.jungleFrom(List.of(jungle(700, 800))).getAsInt()).isEqualTo(700);
    assertThat(farm.jungleFrom(List.of()).getAsInt()).isBetween(600, 720);
  }

  @Test
  void aLaneHeroHasNoJungleStart() {
    HeroFarm farm = HeroFarm.of(1, List.of(kill("LANE_MELEE", 10.0, 40, false)));

    assertThat(farm.jungleFrom(List.of(jungle(0, 100)))).isEmpty();
    assertThat(farm.mix()).isEqualTo(HeroFarm.Mix.LANE);
  }

  @Test
  void anEarlyUnrelatedOrVeryLongSegmentDoesNotMoveTheStartBackBeforeTheJungleMinute() {
    java.util.ArrayList<CreepKillDomain> kills = new java.util.ArrayList<>();
    for (int m = 0; m < 12; m++) {
      kills.add(kill("LANE_MELEE", m * 60.0, 100, false));
    }
    for (int m = 12; m < 20; m++) {
      kills.add(kill("NEUTRAL", m * 60.0, 300, false));
    }
    HeroFarm farm = HeroFarm.of(1, kills);

    assertThat(farm.jungleFrom(List.of(jungle(60, 120), jungle(100, 5000))).getAsInt())
        .isEqualTo(600);
    assertThat(farm.jungleFrom(List.of(jungle(60, 120), jungle(760, 800))).getAsInt())
        .isEqualTo(760);
  }
}
