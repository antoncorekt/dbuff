package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.resources.model.MatchDetailResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchDetailServiceTest {

  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final PlayerGameStatisticRepo playerRepo = mock(PlayerGameStatisticRepo.class);
  private final ItemRepository itemRepo = mock(ItemRepository.class);
  private final AbilityRepo abilityRepo = mock(AbilityRepo.class);
  private final KillLogRepo killLogRepo = mock(KillLogRepo.class);
  private final MatchDetailService service =
      new MatchDetailService(matchRepo, playerRepo, itemRepo, abilityRepo, killLogRepo);

  @Test
  void buildCopiesEveryRepositoryResult() {
    MatchDomain match = mock(MatchDomain.class);
    MatchAnalysisDomain analysis = mock(MatchAnalysisDomain.class);
    List<PlayerMatchStatisticDomain> players = List.of(mock(PlayerMatchStatisticDomain.class));
    List<ItemDomain> items = List.of(mock(ItemDomain.class));
    List<AbilityDomain> abilities = List.of(mock(AbilityDomain.class));
    List<KillLogDomain> killLogs = List.of(mock(KillLogDomain.class));
    when(match.getId()).thenReturn(7L);
    when(match.getAnalysis()).thenReturn(analysis);
    when(playerRepo.findAllByMatchId(7L)).thenReturn(players);
    when(itemRepo.findAllByMatchId(7L)).thenReturn(items);
    when(abilityRepo.findAllByMatchId(7L)).thenReturn(abilities);
    when(killLogRepo.findAllByMatchId(7L)).thenReturn(killLogs);

    MatchDetailResponse response = service.build(match);

    assertThat(response.getMatch()).isSameAs(match);
    assertThat(response.getPlayers()).isSameAs(players);
    assertThat(response.getItems()).isSameAs(items);
    assertThat(response.getAbilities()).isSameAs(abilities);
    assertThat(response.getKillLogs()).isSameAs(killLogs);
    assertThat(response.getAnalysis()).isSameAs(analysis);
  }

  @Test
  void findByIdIsEmptyForUnknownMatch() {
    when(matchRepo.findById(1L)).thenReturn(Optional.empty());

    assertThat(service.findById(1L)).isEmpty();
  }
}
