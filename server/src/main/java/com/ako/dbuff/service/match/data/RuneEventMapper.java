package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.RuneEventDomain;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerRunesLogInner;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

/** Entries without a time or a numeric rune code are skipped and do not use up an index. */
@Component
public class RuneEventMapper extends PlayerLogMapper<RuneEventDomain> {

  public RuneEventMapper(RuneEventRepo repo) {
    super(repo);
  }

  @Override
  List<RuneEventDomain> rowsOf(MatchResponsePlayersInner player, MatchDomain match) {
    List<RuneEventDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInnerRunesLogInner rune :
        CollectionUtils.emptyIfNull(player.getRunesLog())) {
      Integer type = MatchPayload.asInt(rune.getKey());
      if (rune.getTime() == null || type == null) {
        continue;
      }
      rows.add(
          RuneEventDomain.builder()
              .matchId(match.getId())
              .playerSlot(player.getPlayerSlot())
              .playerId(MatchPayload.playerIdOf(player))
              .runeIndex(rows.size())
              .eventTime(rune.getTime())
              .runeType(type)
              .build());
    }
    return rows;
  }
}
