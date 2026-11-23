package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.resources.model.MatchDetailResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("matchDetailResponseService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchDetailService {

  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final ItemRepository itemRepository;
  private final AbilityRepo abilityRepo;
  private final KillLogRepo killLogRepo;

  public Optional<MatchDetailResponse> findById(long matchId) {
    return matchRepo.findById(matchId).map(this::build);
  }

  public MatchDetailResponse build(MatchDomain match) {
    Long matchId = match.getId();
    return MatchDetailResponse.builder()
        .match(match)
        .players(playerGameStatisticRepo.findAllByMatchId(matchId))
        .items(itemRepository.findAllByMatchId(matchId))
        .abilities(abilityRepo.findAllByMatchId(matchId))
        .killLogs(killLogRepo.findAllByMatchId(matchId))
        .analysis(match.getAnalysis())
        .build();
  }
}
