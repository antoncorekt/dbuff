package com.ako.dbuff.service.replay.map;

import com.ako.dbuff.service.replay.MapFacts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapLayoutTest {

  private final MapLayouts layouts = new MapLayouts(new ObjectMapper());
  private final MapLayout layout = layouts.forVersion(layouts.frozenVersion()).orElseThrow();

  @Test
  void zone_recognisesTheBasesAndWhoseTheyAre() {
    assertThat(layout.zone(-5920, -5352))
        .extracting(Zone::kind, Zone::half)
        .containsExactly(Zone.Kind.BASE, Zone.Half.RADIANT);
    assertThat(layout.zone(5528, 5000))
        .extracting(Zone::kind, Zone::half)
        .containsExactly(Zone.Kind.BASE, Zone.Half.DIRE);
  }

  @Test
  void zone_putsAHeroByAFirstTowerOnThatTowersLane() {
    assertThat(layout.zone(4859, -6379).lane()).isEqualTo(Zone.Lane.BOTTOM);
    assertThat(layout.zone(-1544, -1408).lane()).isEqualTo(Zone.Lane.MID);
    assertThat(layout.zone(-6336, 1856).lane()).isEqualTo(Zone.Lane.TOP);
    assertThat(layout.zone(524, 652).half()).isEqualTo(Zone.Half.DIRE);
    assertThat(layout.zone(-1544, -1408).half()).isEqualTo(Zone.Half.RADIANT);
  }

  @Test
  void zone_findsTheRiverBetweenTheLanesAndTheJungleBehindOwnLanes() {
    Zone river = layout.zone(-3196, 2824);
    Zone jungle = layout.zone(-2000, -4000);

    assertThat(river.kind()).isEqualTo(Zone.Kind.RIVER);
    assertThat(jungle.kind()).isEqualTo(Zone.Kind.JUNGLE);
    assertThat(jungle.half()).isEqualTo(Zone.Half.RADIANT);
  }

  @Test
  void zone_namesTheCampAPointIsInside() {
    MapFacts.Camp camp = layout.facts().camps().getFirst();

    Zone zone = layout.zone(camp.originX(), camp.originY());

    assertThat(zone.kind()).isEqualTo(Zone.Kind.JUNGLE);
    assertThat(zone.camp()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void forVersion_hasNoLayoutForAnotherMapVersion() {
    assertThat(layouts.forVersion("0123456789ab")).isEmpty();
    assertThat(layouts.forVersion(null)).isEmpty();
  }

  @Test
  void aLayoutThatCannotBeReadLeavesNoFrozenLayoutInsteadOfFailingStartup() {
    MapLayouts broken = new MapLayouts(new ObjectMapper(), "replay/does-not-exist.json");

    assertThat(broken.frozenVersion()).isNull();
    assertThat(broken.forVersion("anything")).isEmpty();
  }

  @Test
  void version_ignoresOutpostsAndLotusPoolsWhoseNamesCarrySpawnIds() {
    MapFacts frozen = layout.facts();
    java.util.List<MapFacts.Building> changed = new java.util.ArrayList<>(frozen.buildings());
    changed.add(new MapFacts.Building("99999_npc_dota_lotus_pool_dire", 4, 1, 2));
    changed.removeIf(b -> b.name().startsWith("npc_dota_watch_tower"));

    assertThat(new MapFacts(changed, frozen.camps()).version()).isEqualTo(frozen.version());

    changed.removeIf(b -> b.name().equals("dota_goodguys_tower1_top"));
    assertThat(new MapFacts(changed, frozen.camps()).version()).isNotEqualTo(frozen.version());
  }

  @Test
  void aFrozenLayoutHasTheBuildingsAndTwentyEightCampsOfTheMap() {
    assertThat(layout.campCount()).isEqualTo(28);
    assertThat(layouts.frozenVersion()).hasSize(12);
  }
}
