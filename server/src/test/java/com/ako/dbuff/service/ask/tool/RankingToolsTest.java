package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.Resolution;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.ako.dbuff.service.ranking.ItemRankingService;
import com.ako.dbuff.service.ranking.RankingQuery;
import com.ako.dbuff.service.ranking.RankingSort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingToolsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ResolvedPlayer ANN = new ResolvedPlayer(1L, "Ann");
  private static final ResolvedPlayer BOB = new ResolvedPlayer(2L, "Bob");
  private static final AskContext CTX =
      new AskContext("i", "chan", List.of(ANN, BOB), ANN, LocalDate.of(2026, 11, 22), Set.of());

  private final PlayerReferenceResolver resolver = mock(PlayerReferenceResolver.class);
  private final AskToolArgs args = new AskToolArgs(resolver);
  private final AbilityRankingService abilities = mock(AbilityRankingService.class);
  private final ItemRankingService items = mock(ItemRankingService.class);
  private final SkillRankingTool skills = new SkillRankingTool(args, abilities);
  private final ItemRankingTool itemTool = new ItemRankingTool(args, items);

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  private RankingQuery skillQuery(String input) throws Exception {
    skills.call(json(input), CTX);
    ArgumentCaptor<RankingQuery> captor = ArgumentCaptor.forClass(RankingQuery.class);
    verify(abilities).getAbilityRankings(captor.capture());
    return captor.getValue();
  }

  @Test
  void mySkillsByWinRateUseTheAskerAndDefaultLimit() throws Exception {
    RankingQuery query = skillQuery("{\"sort\":\"win_rate\"}");

    assertThat(query.playerIds()).containsExactly(1L);
    assertThat(query.sort()).isEqualTo(RankingSort.WIN_RATE);
    assertThat(query.limit()).isEqualTo(10);
    assertThat(query.minGames()).isZero();
  }

  @Test
  void scopeAllSendsAnEmptyPlayerSet() throws Exception {
    assertThat(skillQuery("{\"scope\":\"all\"}").playerIds()).isEmpty();
  }

  @Test
  void severalPlayersGiveOneCombinedQuery() throws Exception {
    when(resolver.resolve(any(), any())).thenReturn(new Resolution(List.of(ANN, BOB), List.of()));

    assertThat(skillQuery("{\"players\":[\"Ann\",\"Bob\"]}").playerIds())
        .containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void unknownPlayerIsAnError() {
    when(resolver.resolve(any(), any())).thenReturn(new Resolution(List.of(), List.of("Zed")));

    assertThatThrownBy(() -> skills.call(json("{\"players\":[\"Zed\"]}"), CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("Zed");
  }

  @Test
  void badSortAndOutOfRangeLimitAreErrors() {
    assertThatThrownBy(() -> skills.call(json("{\"sort\":\"best\"}"), CTX))
        .isInstanceOf(AskToolException.class);
    assertThatThrownBy(() -> skills.call(json("{\"limit\":51}"), CTX))
        .isInstanceOf(AskToolException.class);
  }

  @Test
  void itemRankingPassesItemFiltersAndMinGames() throws Exception {
    itemTool.call(
        json("{\"items\":[\"Blink Dagger\"],\"exclude_items\":[\"Tango\"],\"min_games\":5}"), CTX);
    ArgumentCaptor<RankingQuery> captor = ArgumentCaptor.forClass(RankingQuery.class);
    verify(items).getItemRankings(captor.capture());

    assertThat(captor.getValue().includedNames()).containsExactly("Blink Dagger");
    assertThat(captor.getValue().excludedNames()).containsExactly("Tango");
    assertThat(captor.getValue().minGames()).isEqualTo(5);
  }
}
