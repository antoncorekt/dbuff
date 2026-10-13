package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.service.replay.analysis.creep.CreepValues;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreepTallyTest {
  private final LaneProperties properties = new LaneProperties();
  private final CreepTally tally =
      new CreepTally(new CreepValues("replay/creep-values.json"), properties);
  private final LaningWindow window = new LaningWindow(0, 480, LaneEnd.JUNGLE);

  private static CreepKillDomain kill(String creepClass, double seconds, boolean denied) {
    return CreepKillDomain.builder()
        .playerSlot(0L)
        .creepClass(creepClass)
        .seconds(seconds)
        .denied(denied)
        .build();
  }

  @Test
  void tally_countsLastHitsByClassAndDeniesSeparately() {
    CreepTally.Result r =
        tally.tally(
            List.of(
                kill("LANE_MELEE", 100, false),
                kill("LANE_MELEE_UPGRADED", 110, false),
                kill("LANE_RANGED", 120, false),
                kill("LANE_SIEGE", 130, false),
                kill("LANE_MELEE", 140, true)),
            window);

    assertThat(r.lastHits()).isEqualTo(4);
    assertThat(r.meleeLastHits()).isEqualTo(2);
    assertThat(r.rangedLastHits()).isEqualTo(1);
    assertThat(r.siegeLastHits()).isEqualTo(1);
    assertThat(r.denies()).isEqualTo(1);
  }

  @Test
  void tally_scoresASiegeCreepAboveAMeleeCreepByItsOwnValue() {
    double melee = tally.tally(List.of(kill("LANE_MELEE", 100, false)), window).score();
    double siege = tally.tally(List.of(kill("LANE_SIEGE", 100, false)), window).score();

    assertThat(siege).isGreaterThan(melee);
  }

  @Test
  void tally_scoresADenyByTheExperienceItTakesAndLessThanALastHit() {
    double lastHit = tally.tally(List.of(kill("LANE_MELEE", 100, false)), window).score();
    double deny = tally.tally(List.of(kill("LANE_MELEE", 100, true)), window).score();

    assertThat(deny).isPositive().isLessThan(lastHit);
  }

  @Test
  void tally_ignoresCampCreepsAndKillsOutsideTheWindow() {
    CreepTally.Result r =
        tally.tally(
            List.of(
                kill("NEUTRAL", 100, false),
                kill("LANE_MELEE", 500, false),
                kill("LANE_MELEE", -5, false)),
            window);

    assertThat(r.lastHits()).isZero();
    assertThat(r.score()).isZero();
  }
}
