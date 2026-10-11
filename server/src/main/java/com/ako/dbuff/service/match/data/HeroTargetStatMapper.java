package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.HeroTargetStatDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TargetStatKind;
import com.ako.dbuff.dao.repo.HeroTargetStatRepo;
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
public class HeroTargetStatMapper implements MatchDetailMapper {

  private final HeroTargetStatRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<HeroTargetStatDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      collect(rows, match, player, TargetStatKind.DAMAGE, player.getDamageTargets());
      collect(rows, match, player, TargetStatKind.ABILITY_USE, player.getAbilityTargets());
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private void collect(
      List<HeroTargetStatDomain> rows,
      MatchDomain match,
      MatchResponsePlayersInner player,
      TargetStatKind kind,
      Object byInflictor) {
    for (Map.Entry<String, Object> inflictor : MatchPayload.asMap(byInflictor).entrySet()) {
      for (Map.Entry<String, Object> target : MatchPayload.asMap(inflictor.getValue()).entrySet()) {
        Long amount = MatchPayload.asLong(target.getValue());
        if (amount == null) {
          continue;
        }
        rows.add(
            HeroTargetStatDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .statKind(kind)
                .inflictor(inflictor.getKey())
                .targetHeroName(target.getKey())
                .amount(amount)
                .build());
      }
    }
  }
}
