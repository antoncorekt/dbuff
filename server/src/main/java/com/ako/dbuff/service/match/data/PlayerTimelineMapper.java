package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerTimelineMapper implements MatchDetailMapper {

  private final PlayerTimelineRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<PlayerTimelineDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      int minutes =
          IntStream.of(
                  size(player.getGoldT()),
                  size(player.getXpT()),
                  size(player.getLhT()),
                  size(player.getDnT()))
              .max()
              .orElse(0);
      for (int minute = 0; minute < minutes; minute++) {
        rows.add(
            PlayerTimelineDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .gameMinute(minute)
                .gold(MatchPayload.at(player.getGoldT(), minute))
                .xp(MatchPayload.at(player.getXpT(), minute))
                .lastHits(MatchPayload.at(player.getLhT(), minute))
                .denies(MatchPayload.at(player.getDnT(), minute))
                .build());
      }
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private static int size(List<?> values) {
    return values == null ? 0 : values.size();
  }
}
