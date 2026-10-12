package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayersMapperTest {

  private final PlayerRepo playerRepo = mock(PlayerRepo.class);
  private final PlayerGameStatisticRepo statisticRepo = mock(PlayerGameStatisticRepo.class);
  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final PlayersMapper mapper =
      new PlayersMapper(
          playerRepo,
          statisticRepo,
          mock(ItemRepository.class),
          mock(AbilityRepo.class),
          mock(KillLogRepo.class),
          constants);

  PlayersMapperTest() {
    when(playerRepo.findById(any())).thenReturn(Optional.of(new PlayerDomain()));
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage")));
  }

  @Test
  void handle_storesPositionEst() {
    PlayerMatchStatisticDomain saved = handle(new MatchResponsePlayersInner().positionEst(3L));

    assertEquals(3L, saved.getPositionEst());
  }

  @Test
  void handle_leavesPositionEstNullWhenOpenDotaSendsNone() {
    assertNull(handle(new MatchResponsePlayersInner()).getPositionEst());
  }

  private PlayerMatchStatisticDomain handle(MatchResponsePlayersInner player) {
    player.accountId(11L).heroId(1L).playerSlot(0L);
    MatchDomain match = new MatchDomain();
    match.setId(100L);

    mapper.handle(match, player, Map.of());

    ArgumentCaptor<PlayerMatchStatisticDomain> captor =
        ArgumentCaptor.forClass(PlayerMatchStatisticDomain.class);
    verify(statisticRepo).save(captor.capture());
    return captor.getValue();
  }
}
