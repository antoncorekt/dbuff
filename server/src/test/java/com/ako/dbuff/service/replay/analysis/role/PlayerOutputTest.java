package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerOutputTest {

  private static PlayerMatchStatisticDomain stat(Long obs, Long sen) {
    PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
    stat.setHeroDamage(10L);
    stat.setHeroHealing(2L);
    stat.setDamageTaken(3L);
    stat.setLastHits(4L);
    stat.setObsPlaced(obs);
    stat.setSenPlaced(sen);
    return stat;
  }

  @Test
  void copiesTheStatsAndSumsTheWards() {
    assertThat(PlayerOutput.from(stat(3L, 4L))).isEqualTo(new PlayerOutput(10L, 2L, 3L, 4L, 7L));
  }

  @Test
  void aMissingWardKindCountsAsZeroUnlessBothAreMissing() {
    assertThat(PlayerOutput.from(stat(null, 4L)).wardsPlaced()).isEqualTo(4L);
    assertThat(PlayerOutput.from(stat(3L, null)).wardsPlaced()).isEqualTo(3L);
    assertThat(PlayerOutput.from(stat(null, null)).wardsPlaced()).isNull();
  }
}
