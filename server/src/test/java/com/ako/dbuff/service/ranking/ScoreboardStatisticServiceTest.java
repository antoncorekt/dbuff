package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.ImageProcessor;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreboardStatisticServiceTest {

  @Mock private ImageProcessor imageProcessor;
  @Mock private ExternalPlayerStatisticService externalPlayerStatisticService;
  @Mock private PlayerRepo playerRepo;
  @Mock private DbufInstanceConfigService instanceConfigService;

  @InjectMocks private ScoreboardStatisticService service;

  @Test
  void getStatistics_excludesFocusPlayersUnknownAndDuplicates() {
    byte[] image = {1, 2, 3};
    Set<Long> focusIds = Set.of(1L, 2L);

    // Scoreboard has our two focus players (case-insensitive match), an UNKNOWN slot, a duplicate,
    // and two genuine opponents.
    when(imageProcessor.extractPlayerNames(image))
        .thenReturn(List.of("Chupapi", "tit", "UNKNOWN", "EnemyOne", "EnemyOne", "EnemyTwo"));
    when(playerRepo.findByAccountIds(focusIds))
        .thenReturn(List.of(player(1L, "Chupapi"), player(2L, "TiT")));
    when(externalPlayerStatisticService.getStatistics(eq(focusIds), any()))
        .thenAnswer(invocation -> response(invocation.getArgument(1)));

    List<ExternalPlayerStatisticResponse> result = service.getStatistics(focusIds, image);

    // Only the two distinct opponents remain, in encounter order.
    assertEquals(
        List.of("EnemyOne", "EnemyTwo"),
        result.stream().map(ExternalPlayerStatisticResponse::getPlayerName).toList());
    verify(externalPlayerStatisticService).getStatistics(focusIds, "EnemyOne");
    verify(externalPlayerStatisticService).getStatistics(focusIds, "EnemyTwo");
    verify(externalPlayerStatisticService, never()).getStatistics(eq(focusIds), eq("Chupapi"));
    verify(externalPlayerStatisticService, never()).getStatistics(eq(focusIds), eq("UNKNOWN"));
  }

  private PlayerDomain player(Long id, String name) {
    return PlayerDomain.builder().id(id).name(name).build();
  }

  private ExternalPlayerStatisticResponse response(String playerName) {
    return ExternalPlayerStatisticResponse.builder().playerName(playerName).build();
  }
}
