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
  void aFrozenLayoutHasTheBuildingsAndTwentyEightCampsOfTheMap() {
    assertThat(layout.campCount()).isEqualTo(28);
    assertThat(layouts.frozenVersion()).hasSize(12);
  }
}
