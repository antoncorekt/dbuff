package com.ako.dbuff.service.match.mapper;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.ScrapperApiService;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import com.ako.dbuff.service.details.DotabuffBuildDetailsParser;
import com.ako.dbuff.service.details.ScrapperService;
import com.ako.dbuff.service.match.data.MatchDetailService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BasicMatchDetailsMapperTest {

  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final MatchDetailService matchDetailService = mock(MatchDetailService.class);
  private final ScrapperApiService scrapperApiService = mock(ScrapperApiService.class);

  private final BasicMatchDetailsMapper mapper =
      new BasicMatchDetailsMapper(
          constants,
          mock(PlayersMapper.class),
          mock(PlayerGameStatisticRepo.class),
          matchRepo,
          mock(ScrapperService.class),
          mock(DotabuffBuildDetailsParser.class),
          scrapperApiService,
          matchDetailService);

  @Test
  void handle_storesDetailRowsBeforePersistingTheMatch() {
    MatchTypeConstant mode = mock(MatchTypeConstant.class);
    when(mode.getName()).thenReturn("game_mode_ability_draft");
    when(constants.getMatchTypeConstantMap()).thenReturn(Map.of("18", mode));
    when(constants.getPatchConstantMap()).thenReturn(Map.of());
    when(constants.getHeroConstantMap()).thenReturn(Map.of());
    MatchResponse response = new MatchResponse();
    response.setGameMode(18L);
    response.setStartTime(1_700_000_000L);
    response.setPlayers(List.of());
    MatchDomain match = MatchDomain.builder().id(1L).build();

    mapper.handle(response, match);

    InOrder order = inOrder(matchDetailService, matchRepo);
    order.verify(matchDetailService).replace(response, match);
    order.verify(matchRepo).save(match);
  }
}
