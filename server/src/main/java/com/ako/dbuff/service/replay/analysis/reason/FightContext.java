package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/** What is stored about one fight, for the contributors that explain it. */
public record FightContext(
    long matchId,
    GameModeProfile mode,
    FightDomain fight,
    List<FightPlayerDomain> players,
    List<FightAbilityUseDomain> abilityUses,
    List<DeathAnalysisDomain> deaths) {

  /** Null when the player has no row in this fight. */
  public Presence presenceOf(long playerSlot) {
    return players.stream()
        .filter(p -> p.getPlayerSlot() == playerSlot)
        .findFirst()
        .map(p -> Presence.valueOf(p.getPresence()))
        .orElse(null);
  }
}
