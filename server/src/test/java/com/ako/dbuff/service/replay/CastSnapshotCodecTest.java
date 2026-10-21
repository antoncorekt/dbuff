package com.ako.dbuff.service.replay;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CastSnapshotCodecTest {

  @Test
  void decode_returnsWhatWasEncoded() {
    List<CastSnapshot> snapshots =
        List.of(
            new CastSnapshot(
                "pudge_meat_hook",
                211.1,
                211.1,
                8,
                247,
                List.of(
                    new CastSnapshot.HeroPoint(0, -6700, 7100, true),
                    new CastSnapshot.HeroPoint(8, 120, -3400, false))),
            new CastSnapshot("tusk_ice_shards", 305.5, -2.0, 3, CastSnapshot.NO_FACING, List.of()));

    assertThat(CastSnapshotCodec.decode(CastSnapshotCodec.encode(snapshots))).isEqualTo(snapshots);
  }

  @Test
  void encode_refusesAPositionThatDoesNotFitTheFormat() {
    List<CastSnapshot> bad =
        List.of(
            new CastSnapshot(
                "a", 1, 1, 0, 0, List.of(new CastSnapshot.HeroPoint(0, 40_000, 0, true))));

    assertThatThrownBy(() -> CastSnapshotCodec.encode(bad))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
