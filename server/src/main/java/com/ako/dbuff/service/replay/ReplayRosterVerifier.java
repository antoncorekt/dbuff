package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Checks that a replay belongs to the match it was fetched for. The replay's own match id is a
 * lobby id and cannot be compared, so the roster is the proof: the same players on the same slots.
 */
@Component
public class ReplayRosterVerifier {
  private static final int PLAYERS_PER_MATCH = 10;
  private static final String HERO_PREFIX = "npc_dota_hero_";

  /**
   * @throws ReplayException {@code FAILED_IDENTITY} on the first mismatch
   */
  public void verify(
      long matchId, List<ReplayPlayerFact> replayPlayers, List<PlayerMatchStatisticDomain> stored) {
    if (replayPlayers.size() != PLAYERS_PER_MATCH) {
      throw mismatch(matchId, "replay lists " + replayPlayers.size() + " players");
    }
    Map<Long, PlayerMatchStatisticDomain> bySlot =
        stored.stream()
            .collect(
                Collectors.toMap(
                    PlayerMatchStatisticDomain::getPlayerSlot, Function.identity(), (a, b) -> a));
    for (ReplayPlayerFact player : replayPlayers) {
      PlayerMatchStatisticDomain expected = bySlot.get(player.playerSlot());
      if (expected == null) {
        throw mismatch(matchId, "no stored player on slot " + player.playerSlot());
      }
      checkPlayer(matchId, player, expected);
    }
  }

  private void checkPlayer(
      long matchId, ReplayPlayerFact player, PlayerMatchStatisticDomain expected) {
    Long replayAccount = player.accountId();
    Long storedAccount = expected.getPlayerId();
    boolean accountsKnown = replayAccount != null && storedAccount != null && storedAccount > 0;
    if (accountsKnown) {
      if (!replayAccount.equals(storedAccount)) {
        throw mismatch(matchId, "slot " + player.playerSlot() + " has a different account");
      }
      return;
    }
    if (!sameHero(player.heroEntityName(), expected.getHeroName())) {
      throw mismatch(
          matchId,
          "slot "
              + player.playerSlot()
              + " has hero "
              + player.heroEntityName()
              + ", expected "
              + expected.getHeroName());
    }
  }

  /** Replay hero names are CamelCase entity names; OpenDota's are snake_case with a prefix. */
  static boolean sameHero(String entityName, String openDotaName) {
    if (entityName == null || openDotaName == null) {
      return false;
    }
    String stored =
        openDotaName.startsWith(HERO_PREFIX)
            ? openDotaName.substring(HERO_PREFIX.length())
            : openDotaName;
    String snake = entityName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    return snake.equals(stored) || snake.replace("_", "").equals(stored.replace("_", ""));
  }

  private static ReplayException mismatch(long matchId, String detail) {
    return new ReplayException(
        ReplayStatus.FAILED_IDENTITY, "Replay does not match match " + matchId + ": " + detail);
  }
}
