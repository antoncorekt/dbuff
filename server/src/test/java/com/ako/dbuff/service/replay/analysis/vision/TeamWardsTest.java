package com.ako.dbuff.service.replay.analysis.vision;

import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TeamWardsTest {
  private static final VisionProperties P = new VisionProperties();

  /** A ward at world (worldX, 0); the fight is centred on the origin. */
  private static WardEventDomain ward(
      long slot, WardKind kind, long placed, double worldX, Long removed, WardRemovalKind how) {
    return WardEventDomain.builder()
        .playerSlot(slot)
        .wardKind(kind)
        .placedTime(placed)
        .x(worldX / 128 + 128)
        .y(128.0)
        .removedTime(removed)
        .removalKind(how)
        .build();
  }

  private static TeamWards radiantAt1000(WardEventDomain... wards) {
    return TeamWards.at(List.of(wards), TeamSide.RADIANT, 0, 0, 1000, 1020, P);
  }

  @Test
  void anOldObserverNearTheFightCountsFully_withItsAge() {
    TeamWards w =
        radiantAt1000(ward(0, WardKind.OBSERVER, 800, 500, null, WardRemovalKind.UNKNOWN));

    assertThat(w.observers()).isEqualTo(1);
    assertThat(w.youngestAge()).isEqualTo(200);
    assertThat(w.oldestAge()).isEqualTo(200);
    assertThat(w.weight()).isEqualTo(1.0, within(1e-9));
    assertThat(w.late()).isFalse();
  }

  @Test
  void anObserverPlacedWithinThirtySecondsIsLateAndCountsHalf() {
    TeamWards w =
        radiantAt1000(
            ward(0, WardKind.OBSERVER, 985, 500, null, WardRemovalKind.UNKNOWN),
            ward(1, WardKind.OBSERVER, 700, 500, null, WardRemovalKind.UNKNOWN));

    assertThat(w.weight()).isEqualTo(1.5, within(1e-9));
    assertThat(w.lateSeconds()).isEqualTo(15);
    assertThat(w.youngestAge()).isEqualTo(15);
    assertThat(w.oldestAge()).isEqualTo(300);
  }

  @Test
  void aWardTooFarAway_anEnemyWard_aDeadWard_andAFutureWardDoNotCount() {
    TeamWards w =
        radiantAt1000(
            ward(0, WardKind.OBSERVER, 800, 2000, null, WardRemovalKind.UNKNOWN),
            ward(128, WardKind.OBSERVER, 800, 100, null, WardRemovalKind.UNKNOWN),
            ward(0, WardKind.OBSERVER, 700, 100, 990L, WardRemovalKind.KILLED),
            ward(0, WardKind.OBSERVER, 1010, 100, null, WardRemovalKind.UNKNOWN),
            ward(0, WardKind.OBSERVER, 500, 100, null, WardRemovalKind.UNKNOWN));

    assertThat(w.observers()).as("the last one expired at 860").isZero();
    assertThat(w.weight()).isZero();
  }

  @Test
  void aSentryUsesItsSmallerRadius_andIsNotAnObserver() {
    TeamWards w =
        radiantAt1000(
            ward(0, WardKind.SENTRY, 800, 800, null, WardRemovalKind.UNKNOWN),
            ward(0, WardKind.SENTRY, 800, 1000, null, WardRemovalKind.UNKNOWN));

    assertThat(w.sentries()).isEqualTo(1);
    assertThat(w.observers()).isZero();
  }

  @Test
  void aSentryPlacedNearTheFightJustBeforeOrDuringItIsNoted() {
    assertThat(
            radiantAt1000(ward(0, WardKind.SENTRY, 990, 100, null, WardRemovalKind.UNKNOWN))
                .sentryPlacedAround())
        .isTrue();
    assertThat(
            radiantAt1000(ward(0, WardKind.SENTRY, 1010, 100, null, WardRemovalKind.UNKNOWN))
                .sentryPlacedAround())
        .isTrue();
    assertThat(
            radiantAt1000(ward(0, WardKind.SENTRY, 900, 100, null, WardRemovalKind.UNKNOWN))
                .sentryPlacedAround())
        .isFalse();
  }

  @Test
  void aWardKilledInTheWindowIsDestroyedByTheOtherTeamOnly() {
    List<WardEventDomain> wards =
        List.of(
            ward(128, WardKind.OBSERVER, 800, 0, 950L, WardRemovalKind.KILLED),
            ward(128, WardKind.OBSERVER, 800, 0, 1300L, WardRemovalKind.KILLED),
            ward(128, WardKind.OBSERVER, 100, 0, 460L, WardRemovalKind.EXPIRED),
            ward(0, WardKind.OBSERVER, 800, 0, 960L, WardRemovalKind.KILLED));

    assertThat(TeamWards.destroyedBy(wards, TeamSide.RADIANT, 900, 1035)).hasSize(1);
    assertThat(TeamWards.destroyedBy(wards, TeamSide.DIRE, 900, 1035)).hasSize(1);
  }
}
