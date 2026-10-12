package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.service.replay.analysis.composition.CompositionStage;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import java.util.Optional;

/**
 * What is stored about one fight, for the contributors that explain it, with the roles and team
 * compositions of the whole match; both lists are empty when the match has none stored.
 */
public record FightContext(
    long matchId,
    GameModeProfile mode,
    FightDomain fight,
    List<FightPlayerDomain> players,
    List<FightAbilityUseDomain> abilityUses,
    List<DeathAnalysisDomain> deaths,
    List<PlayerRoleDomain> roles,
    List<TeamCompositionDomain> compositions) {

  public FightContext(
      long matchId,
      GameModeProfile mode,
      FightDomain fight,
      List<FightPlayerDomain> players,
      List<FightAbilityUseDomain> abilityUses,
      List<DeathAnalysisDomain> deaths) {
    this(matchId, mode, fight, players, abilityUses, deaths, List.of(), List.of());
  }

  /** Empty when the player has no row in this fight. */
  public Optional<FightPlayerDomain> player(long playerSlot) {
    return players.stream()
        .filter(p -> p.getPlayerSlot() != null && p.getPlayerSlot() == playerSlot)
        .findFirst();
  }

  /** Null when the player has no row in this fight, or the row has no known presence. */
  public Presence presenceOf(long playerSlot) {
    return player(playerSlot).map(p -> Enums.find(Presence.class, p.getPresence())).orElse(null);
  }

  public Optional<TeamCompositionDomain> composition(TeamSide team, CompositionStage stage) {
    return compositions.stream()
        .filter(c -> team.name().equals(c.getTeam()) && stage.name().equals(c.getStage()))
        .findFirst();
  }

  /** The team that lost the fight; empty for a draw or a fight with no known winner. */
  public Optional<TeamSide> loser() {
    TeamSide winner = fight == null ? null : Enums.find(TeamSide.class, fight.getWinner());
    if (winner == null) {
      return Optional.empty();
    }
    return Optional.of(winner == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT);
  }
}
