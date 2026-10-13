package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRowRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

/** A mapper whose rows come from one log per player; players without a slot are skipped. */
abstract class PlayerLogMapper<T> implements MatchDetailMapper {

  private final MatchRowRepo<T> repo;

  PlayerLogMapper(MatchRowRepo<T> repo) {
    this.repo = repo;
  }

  /** The rows of one player, with the match and player columns already set. */
  abstract List<T> rowsOf(MatchResponsePlayersInner player, MatchDomain match);

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<T> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() != null) {
        rows.addAll(rowsOf(player, match));
      }
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
