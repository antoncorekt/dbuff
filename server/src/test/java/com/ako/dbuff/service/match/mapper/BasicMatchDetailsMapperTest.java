package com.ako.dbuff.service.match.mapper;

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
import com.ako.dbuff.service.replay.ReplayRequestAnnouncer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BasicMatchDetailsMapperTest {

  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final MatchDetailService matchDetailService = mock(MatchDetailService.class);
  private final ScrapperApiService scrapperApiService = mock(ScrapperApiService.class);
  private final ReplayRequestAnnouncer announcer = mock(ReplayRequestAnnouncer.class);

  private final BasicMatchDetailsMapper mapper =
      new BasicMatchDetailsMapper(
          constants,
          mock(PlayersMapper.class),
          mock(PlayerGameStatisticRepo.class),
          matchRepo,
          mock(ScrapperService.class),
          mock(DotabuffBuildDetailsParser.class),
          scrapperApiService,
          matchDetailService,
          announcer);

  @Test
  void handle_storesDetailRowsBeforePersistingTheMatch() {
    MatchResponse response = ordinaryMatch();
    MatchDomain match = MatchDomain.builder().id(1L).build();

    mapper.handle(response, match);

    InOrder order = inOrder(matchDetailService, matchRepo);
    order.verify(matchDetailService).replace(response, match);
    order.verify(matchRepo).save(match);
    verify(announcer).announce(match);
  }

  @Test
  void handle_stillPersistsTheMatchWhenDetailRowsCannotBeStored() {
    MatchResponse response = ordinaryMatch();
    MatchDomain match = MatchDomain.builder().id(1L).build();
    doThrow(new IllegalStateException("bad shape"))
        .when(matchDetailService)
        .replace(response, match);

    mapper.handle(response, match);

    verify(matchRepo).save(match);
  }

  private MatchResponse ordinaryMatch() {
    MatchTypeConstant mode = mock(MatchTypeConstant.class);
    when(mode.getName()).thenReturn("game_mode_ability_draft");
    when(constants.getMatchTypeConstantMap()).thenReturn(Map.of("18", mode));
    when(constants.getPatchConstantMap()).thenReturn(Map.of());
    when(constants.getHeroConstantMap()).thenReturn(Map.of());
    MatchResponse response = new MatchResponse();
    response.setGameMode(18L);
    response.setStartTime(1_700_000_000L);
    response.setPlayers(List.of());
    return response;
  }
}
