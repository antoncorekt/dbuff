package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayRosterVerifierTest {

  private static final long STEAM_OFFSET = 76561197960265728L;
  private final ReplayRosterVerifier verifier = new ReplayRosterVerifier();

  @Test
  void verify_acceptsAReplayWhoseAccountsMatchTheStoredPlayers() {
    assertThatCode(() -> verifier.verify(1L, replayPlayers(), storedPlayers()))
        .doesNotThrowAnyException();
  }

  @Test
  void verify_rejectsAReplayWithADifferentAccountOnASlot() {
    List<PlayerMatchStatisticDomain> stored = storedPlayers();
    stored.get(3).setPlayerId(999L);

    assertThatThrownBy(() -> verifier.verify(1L, replayPlayers(), stored))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_IDENTITY));
  }

  @Test
  void verify_rejectsAReplayThatDoesNotListTenPlayers() {
    List<ReplayPlayerFact> nine = replayPlayers().subList(0, 9);

    assertThatThrownBy(() -> verifier.verify(1L, nine, storedPlayers()))
        .isInstanceOfSatisfying(
            ReplayException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ReplayStatus.FAILED_IDENTITY));
  }

  @Test
  void verify_fallsBackToTheHeroWhenTheAccountIsAnonymous() {
    List<ReplayPlayerFact> replay = new ArrayList<>(replayPlayers());
    replay.set(2, new ReplayPlayerFact(4, 2, 0L, "ObsidianDestroyer"));
    List<PlayerMatchStatisticDomain> stored = storedPlayers();
    stored.get(2).setPlayerId(null);
    stored.get(2).setHeroName("npc_dota_hero_obsidian_destroyer");

    assertThatCode(() -> verifier.verify(1L, replay, stored)).doesNotThrowAnyException();

    stored.get(2).setHeroName("npc_dota_hero_sniper");
    assertThatThrownBy(() -> verifier.verify(1L, replay, stored))
        .isInstanceOf(ReplayException.class);
  }

  private static List<ReplayPlayerFact> replayPlayers() {
    List<ReplayPlayerFact> players = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      int replayId = i * 2;
      players.add(
          new ReplayPlayerFact(replayId, i < 5 ? 2 : 3, STEAM_OFFSET + 100 + i, "Hero" + i));
    }
    return players;
  }

  private static List<PlayerMatchStatisticDomain> storedPlayers() {
    List<PlayerMatchStatisticDomain> stored = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
      stat.setPlayerSlot(i < 5 ? i : 128L + i - 5);
      stat.setPlayerId(100L + i);
      stat.setHeroName("npc_dota_hero_hero" + i);
      stored.add(stat);
    }
    return stored;
  }
}
