package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TowerFallTest {
  private static BuildingKillContextDomain.BuildingKillContextDomainBuilder building(String kind) {
    return BuildingKillContextDomain.builder().kind(kind).ownerTeam("DIRE").seconds(100.0);
  }

  @Test
  void of_keepsTowersWithATimeAndAKnownOwner() {
    List<TowerFall> falls =
        TowerFall.of(
            List.of(
                building("TOWER").lane("TOP").denied(true).build(),
                building("TOWER").build(),
                building("BARRACKS_MELEE").build(),
                building("TOWER").seconds(null).build(),
                building("TOWER").ownerTeam(null).build()));

    assertThat(falls)
        .containsExactly(
            new TowerFall(TeamSide.DIRE, 100.0, Zone.Lane.TOP, true),
            new TowerFall(TeamSide.DIRE, 100.0, null, false));
  }

  @Test
  void ofObjectives_keepsTheBarracksAndTheAncientToo() {
    List<TowerFall> falls =
        TowerFall.ofObjectives(
            List.of(
                building("TOWER").build(),
                building("BARRACKS_MELEE").build(),
                building("BARRACKS_RANGED").build(),
                building("FORT").build(),
                building("OTHER").build(),
                building(null).build(),
                building("FORT").seconds(null).build()));

    assertThat(falls).hasSize(4);
  }

  @Test
  void opponent_isTheOtherTeam() {
    assertThat(TeamSide.RADIANT.opponent()).isEqualTo(TeamSide.DIRE);
    assertThat(TeamSide.DIRE.opponent()).isEqualTo(TeamSide.RADIANT);
  }
}
