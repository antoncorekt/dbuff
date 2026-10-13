package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.RuneEventDomain;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerRunesLogInner;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuneEventMapper implements MatchDetailMapper {

  private final RuneEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<RuneEventDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      List<MatchResponsePlayersInnerRunesLogInner> runes = player.getRunesLog();
      for (int i = 0; runes != null && i < runes.size(); i++) {
        MatchResponsePlayersInnerRunesLogInner rune = runes.get(i);
        rows.add(
            RuneEventDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .runeIndex(i)
                .eventTime(rune.getTime())
                .runeType(MatchPayload.asInt(rune.getKey()))
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
