package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSlotsTest {

  @Test
  void slotAndIndex_areEachOthersInverseForAllTenPlayers() {
    for (int index = 0; index < 10; index++) {
      assertThat(PlayerSlots.index(PlayerSlots.slot(index))).isEqualTo(index);
    }
    assertThat(PlayerSlots.slot(0)).isEqualTo(0);
    assertThat(PlayerSlots.slot(4)).isEqualTo(4);
    assertThat(PlayerSlots.slot(5)).isEqualTo(128);
    assertThat(PlayerSlots.slot(9)).isEqualTo(132);
  }
}
