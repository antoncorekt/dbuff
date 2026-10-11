package com.ako.dbuff.service.replay.analysis.building;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildingNameTest {

  @Test
  void parse_readsTowersBarracksAndTheAncient() {
    assertThat(BuildingName.parse("npc_dota_goodguys_tower1_bot"))
        .contains(new BuildingName("RADIANT", "TOWER", 1, "BOTTOM"));
    assertThat(BuildingName.parse("npc_dota_badguys_tower4"))
        .contains(new BuildingName("DIRE", "TOWER", 4, null));
    assertThat(BuildingName.parse("npc_dota_badguys_melee_rax_top"))
        .contains(new BuildingName("DIRE", "BARRACKS_MELEE", null, "TOP"));
    assertThat(BuildingName.parse("npc_dota_goodguys_range_rax_mid"))
        .contains(new BuildingName("RADIANT", "BARRACKS_RANGED", null, "MID"));
    assertThat(BuildingName.parse("npc_dota_goodguys_fort"))
        .contains(new BuildingName("RADIANT", "FORT", null, null));
  }

  @Test
  void parse_leavesOutFillersAndNonsense() {
    assertThat(BuildingName.parse("npc_dota_goodguys_fillers")).isEmpty();
    assertThat(BuildingName.parse("npc_dota_hero_axe")).isEmpty();
    assertThat(BuildingName.parse(null)).isEmpty();
  }
}
