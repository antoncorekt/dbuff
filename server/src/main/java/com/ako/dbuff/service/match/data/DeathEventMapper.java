package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerDeathsLogInner;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeathEventMapper implements MatchDetailMapper {

  private final DeathEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<DeathEventDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      List<MatchResponsePlayersInnerDeathsLogInner> deaths = player.getDeathsLog();
      for (int i = 0; deaths != null && i < deaths.size(); i++) {
        MatchResponsePlayersInnerDeathsLogInner death = deaths.get(i);
        rows.add(
            DeathEventDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .deathIndex(i)
                .deathTime(death.getTime())
                .killerHeroName(death.getKey())
                .goldLost(death.getGoldLost())
                .goldFed(death.getGoldFed())
                .timeDead(death.getTimeDead())
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
