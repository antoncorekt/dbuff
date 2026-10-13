package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.service.replay.map.Zone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreepClassTest {

  @ParameterizedTest
  @CsvSource({
    "npc_dota_creep_goodguys_melee, LANE_MELEE",
    "npc_dota_creep_badguys_ranged, LANE_RANGED",
    "npc_dota_creep_badguys_flagbearer, LANE_FLAGBEARER",
    "npc_dota_goodguys_siege, LANE_SIEGE",
    "npc_dota_badguys_siege, LANE_SIEGE",
    "npc_dota_goodguys_siege_upgraded, LANE_SIEGE_UPGRADED",
    "npc_dota_creep_goodguys_melee_upgraded, LANE_MELEE_UPGRADED",
    "npc_dota_creep_goodguys_ranged_upgraded, LANE_RANGED_UPGRADED",
    "npc_dota_creep_goodguys_flagbearer_upgraded, LANE_FLAGBEARER_UPGRADED",
    "npc_dota_creep_badguys_melee_upgraded_mega, LANE_MEGA",
    "npc_dota_creep_badguys_something_new, LANE_OTHER",
    "npc_dota_neutral_centaur_khan, NEUTRAL",
    "npc_dota_neutral_black_dragon, NEUTRAL_ANCIENT",
    "npc_dota_neutral_ancient_frog, NEUTRAL_ANCIENT"
  })
  void of_namesTheClassOfEachLaneAndCampCreep(String unit, CreepClass expected) {
    assertThat(CreepClass.of(unit)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "npc_dota_hero_luna",
        "npc_dota_goodguys_melee_rax_mid",
        "npc_dota_badguys_tower1_top",
        "npc_dota_goodguys_fort",
        "npc_dota_roshan",
        "npc_dota_observer_wards",
        "npc_dota_lycan_wolf1",
        "npc_dota_dark_troll_warlord_skeleton_warrior",
        "npc_dota_courier",
        ""
      })
  void of_isNullForWhatIsNoLaneOrCampCreep(String unit) {
    assertThat(CreepClass.of(unit)).isNull();
  }

  @Test
  void of_isNullForNoName() {
    assertThat(CreepClass.of(null)).isNull();
  }

  @Test
  void laneAndNeutralClassesAreToldApart() {
    assertThat(CreepClass.LANE_SIEGE.isLane()).isTrue();
    assertThat(CreepClass.LANE_SIEGE.isNeutral()).isFalse();
    assertThat(CreepClass.NEUTRAL_ANCIENT.isNeutral()).isTrue();
    assertThat(CreepClass.LANE_FLAGBEARER_UPGRADED.isFlagbearer()).isTrue();
    assertThat(CreepClass.LANE_MELEE.isFlagbearer()).isFalse();
  }

  @Test
  void sideOf_readsTheSideFromTheNameAndIsNullForACamp() {
    assertThat(CreepClass.sideOf("npc_dota_creep_goodguys_melee")).isEqualTo(Zone.Half.RADIANT);
    assertThat(CreepClass.sideOf("npc_dota_badguys_siege")).isEqualTo(Zone.Half.DIRE);
    assertThat(CreepClass.sideOf("npc_dota_neutral_kobold")).isNull();
  }
}
