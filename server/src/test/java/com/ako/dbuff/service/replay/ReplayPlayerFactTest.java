package com.ako.dbuff.service.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayPlayerFactTest {

  @Test
  void playerSlot_halvesTheReplayIdAndMapsDireToTheHighSlots() {
    assertThat(fact(0).playerSlot()).isEqualTo(0);
    assertThat(fact(8).playerSlot()).isEqualTo(4);
    assertThat(fact(10).playerSlot()).isEqualTo(128);
    assertThat(fact(18).playerSlot()).isEqualTo(132);
  }

  @Test
  void accountId_isTheSteamIdWithoutTheOffsetAndNullWhenAnonymous() {
    assertThat(new ReplayPlayerFact(0, 2, 76561197960265728L + 12345L, "X").accountId())
        .isEqualTo(12345L);
    assertThat(new ReplayPlayerFact(0, 2, 0L, "X").accountId()).isNull();
  }

  private static ReplayPlayerFact fact(int replayId) {
    return new ReplayPlayerFact(replayId, 2, 0L, "X");
  }
}
