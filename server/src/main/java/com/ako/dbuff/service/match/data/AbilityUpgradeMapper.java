package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.AbilityUpgradeDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityUpgradeRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AbilityUpgradeMapper implements MatchDetailMapper {

  private final AbilityUpgradeRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<AbilityUpgradeDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      List<Long> upgrades = player.getAbilityUpgradesArr();
      for (int i = 0; upgrades != null && i < upgrades.size(); i++) {
        rows.add(
            AbilityUpgradeDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .upgradeIndex(i + 1)
                .abilityId(upgrades.get(i))
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
