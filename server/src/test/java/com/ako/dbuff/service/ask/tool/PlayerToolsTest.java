package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.Resolution;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.PlayerStatisticService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerToolsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ResolvedPlayer ANN = new ResolvedPlayer(1L, "Ann");
  private static final ResolvedPlayer BOB = new ResolvedPlayer(2L, "Bob");
  private static final AskContext CTX =
      new AskContext("i", "chan", List.of(ANN, BOB), ANN, LocalDate.of(2026, 11, 22), Set.of());

  private final PlayerReferenceResolver resolver = mock(PlayerReferenceResolver.class);
  private final AskToolArgs args = new AskToolArgs(resolver);
  private final PlayerStatisticService stats = mock(PlayerStatisticService.class);
  private final AbilityRankingService abilities = mock(AbilityRankingService.class);

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  void twoPlayersGiveTwoServiceCalls() throws Exception {
    when(resolver.resolve(any(), any())).thenReturn(new Resolution(List.of(ANN, BOB), List.of()));

    Object result =
        new PlayerStatsTool(args, stats).call(json("{\"players\":[\"Ann\",\"Bob\"]}"), CTX);

    assertThat((List<?>) result).hasSize(2);
    assertThat(((Map<?, ?>) ((List<?>) result).get(1)).get("player")).isEqualTo("Bob");
    verify(stats).getPlayerStatistics(eq(1L), any(), any(), any(), any(), any());
    verify(stats).getPlayerStatistics(eq(2L), any(), any(), any(), any(), any());
  }

  @Test
  void badDateIsAnError() {
    assertThatThrownBy(
            () -> new PlayerStatsTool(args, stats).call(json("{\"from\":\"soon\"}"), CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("from");
    verify(stats, never()).getPlayerStatistics(any(), any(), any(), any(), any(), any());
  }

  @Test
  void matchesDefaultToTwentyAndRejectTooMany() throws Exception {
    PlayerMatchesTool tool = new PlayerMatchesTool(args, stats);
    tool.call(json("{}"), CTX);

    verify(stats).getPlayerMatches(eq(1L), any(), any(), any(), any(), any(), eq(20));
    assertThatThrownBy(() -> tool.call(json("{\"limit\":101}"), CTX))
        .isInstanceOf(AskToolException.class);
  }

  @Test
  void savedSummariesUseTheRangeOnlyWhenGiven() throws Exception {
    SavedSummariesTool tool = new SavedSummariesTool(args, stats);
    tool.call(json("{}"), CTX);
    tool.call(json("{\"from\":\"2026-10-01\"}"), CTX);

    verify(stats).getPersistedStatistics(1L);
    verify(stats).getPersistedStatistics(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 22));
  }

  @Test
  void comboToolsNeedSkillsAndOnePlayer() throws Exception {
    SkillComboStatsTool tool = new SkillComboStatsTool(args, abilities);
    assertThatThrownBy(() -> tool.call(json("{}"), CTX)).isInstanceOf(AskToolException.class);

    when(resolver.resolve(any(), any())).thenReturn(new Resolution(List.of(ANN, BOB), List.of()));
    assertThatThrownBy(
            () -> tool.call(json("{\"skills\":[\"Blink\"],\"players\":[\"a\",\"b\"]}"), CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("one player");
  }
}
