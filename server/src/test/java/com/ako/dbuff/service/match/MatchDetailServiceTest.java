package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.MatchDomain;
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
    when(match.getId()).thenReturn(7L);
    when(playerRepo.findAllByMatchId(7L)).thenReturn(List.of());
    when(itemRepo.findAllByMatchId(7L)).thenReturn(List.of());
    when(abilityRepo.findAllByMatchId(7L)).thenReturn(List.of());
    when(killLogRepo.findAllByMatchId(7L)).thenReturn(List.of());

    MatchDetailResponse response = service.build(match);

    assertThat(response.getMatch()).isSameAs(match);
    assertThat(response.getPlayers()).isEmpty();
    assertThat(response.getItems()).isEmpty();
    assertThat(response.getAbilities()).isEmpty();
    assertThat(response.getKillLogs()).isEmpty();
  }

  @Test
  void findByIdIsEmptyForUnknownMatch() {
    when(matchRepo.findById(1L)).thenReturn(Optional.empty());

    assertThat(service.findById(1L)).isEmpty();
  }
}
