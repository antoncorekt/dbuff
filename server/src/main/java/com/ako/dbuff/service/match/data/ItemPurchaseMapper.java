package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.ItemPurchaseEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerPurchaseLogInner;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemPurchaseMapper implements MatchDetailMapper {

  private final ItemPurchaseEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<ItemPurchaseEventDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      List<MatchResponsePlayersInnerPurchaseLogInner> log = player.getPurchaseLog();
      for (int i = 0; log != null && i < log.size(); i++) {
        MatchResponsePlayersInnerPurchaseLogInner purchase = log.get(i);
        rows.add(
            ItemPurchaseEventDomain.builder()
                .matchId(match.getId())
                .playerSlot(player.getPlayerSlot())
                .playerId(MatchPayload.playerIdOf(player))
                .purchaseIndex(i)
                .purchaseTime(purchase.getTime())
                .itemKey(purchase.getKey())
                .charges(purchase.getCharges())
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
