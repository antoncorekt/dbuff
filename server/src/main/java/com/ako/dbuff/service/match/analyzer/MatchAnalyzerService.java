package com.ako.dbuff.service.match.analyzer;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MatchAnalyzerService {

  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final AbilityRepo abilityRepo;
  private final ItemRepository itemRepository;

  private final ConstantsManagers constantManagers;

  private static final String SUMMARY_FORMAT = "%s - ";

  public String prepareMatchTextSummary(MatchDomain matchDomain, DbufInstanceConfigDomain config) {
    Set<Long> playerIds = config.getPlayerIds();

    String winLostStatus = getWinLostStatus(matchDomain, playerIds);

    String matchType =
        Optional.ofNullable(matchDomain.getGameModeName())
            .map(gm -> gm.replace("game_mode_", "").replace("_", " "))
            .orElse("unknown");

    if (matchDomain.getAbadon() == true) {
      return String.format(
          "%s - match [%d]; mode [%s] - abandoned", winLostStatus, matchDomain.getId(), matchType);
    }

    return String.format(
        "%s - match [%d]; mode [%s]", winLostStatus, matchDomain.getId(), matchType);
  }

  private String getWinLostStatus(MatchDomain matchDomain, Set<Long> playerIds) {

    // todo impl
    return "";
  }

  // todo set response type to some tipe
  public String analyze(Long matchId, DbufInstanceConfigDomain config) {

    MatchDomain matchDomain =
        matchRepo
            .findById(matchId)
            .orElseThrow(() -> new RuntimeException("match " + matchId + " not found"));

    List<PlayerMatchStatisticDomain> statistic = playerGameStatisticRepo.findAllByMatchId(matchId);

    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(matchId);

    List<ItemDomain> items = itemRepository.findAllByMatchId(matchId);

    return "";
  }
}
