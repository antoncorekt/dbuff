package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.UnitKillDomain;
import com.ako.dbuff.dao.repo.UnitKillRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Entries whose count is not a number are skipped. */
@Component
public class UnitKillMapper extends PlayerLogMapper<UnitKillDomain> {

  public UnitKillMapper(UnitKillRepo repo) {
    super(repo);
  }

  @Override
  List<UnitKillDomain> rowsOf(MatchResponsePlayersInner player, MatchDomain match) {
    List<UnitKillDomain> rows = new ArrayList<>();
    for (Map.Entry<String, Object> kill : MatchPayload.asMap(player.getKilled()).entrySet()) {
      Long count = MatchPayload.asLong(kill.getValue());
      if (count == null) {
        continue;
      }
      rows.add(
          UnitKillDomain.builder()
              .matchId(match.getId())
              .playerSlot(player.getPlayerSlot())
              .playerId(MatchPayload.playerIdOf(player))
              .unitName(kill.getKey())
              .killCount(count)
              .build());
    }
    return rows;
  }
}
