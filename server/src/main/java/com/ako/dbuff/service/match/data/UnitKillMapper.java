package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.UnitKillDomain;
import com.ako.dbuff.dao.repo.UnitKillRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnitKillMapper implements MatchDetailMapper {

  private final UnitKillRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<UnitKillDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      for (Map.Entry<String, Object> kill : MatchPayload.asMap(player.getKilled()).entrySet()) {
        rows.add(
            UnitKillDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .unitName(kill.getKey())
                .killCount(MatchPayload.asLong(kill.getValue()))
                .build());
      }
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }
}
