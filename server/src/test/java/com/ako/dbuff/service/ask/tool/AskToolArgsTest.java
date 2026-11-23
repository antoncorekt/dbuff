package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.Resolution;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AskToolArgsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ResolvedPlayer ANN = new ResolvedPlayer(1L, "Ann");
  private static final ResolvedPlayer BOB = new ResolvedPlayer(2L, "Bob");
  private static final AskContext CTX =
      new AskContext("i", "chan", List.of(ANN, BOB), ANN, LocalDate.of(2026, 11, 22), Set.of());

  private final PlayerReferenceResolver resolver = mock(PlayerReferenceResolver.class);
  private final AskToolArgs args = new AskToolArgs(resolver);

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  void emptyPlayersGiveTheAsker() throws Exception {
    assertThat(args.players(json("{}"), "players", CTX)).containsExactly(ANN);
    assertThat(args.players(json("{\"players\":[]}"), "players", CTX)).containsExactly(ANN);
    verifyNoInteractions(resolver);
  }

  @Test
  void namedPlayersAreResolvedThroughTheChannel() throws Exception {
    when(resolver.resolve("chan", List.of("Bob", "1")))
        .thenReturn(new Resolution(List.of(BOB, ANN), List.of()));

    assertThat(args.players(json("{\"players\":[\"Bob\",1]}"), "players", CTX))
        .containsExactly(BOB, ANN);
  }

  @Test
  void unresolvedPlayerNamesTheValidOnes() throws Exception {
    when(resolver.resolve(any(), any())).thenReturn(new Resolution(List.of(), List.of("Zed")));

    assertThatThrownBy(() -> args.players(json("{\"players\":[\"Zed\"]}"), "players", CTX))
        .isInstanceOf(AskToolException.class)
        .hasMessageContaining("Zed")
        .hasMessageContaining("Ann, Bob");
  }

  @Test
  void scalarHelpers() throws Exception {
    JsonNode in = json("{\"s\":[\"a\",\" b \",\"\"],\"d\":\"2026-01-31\",\"n\":7,\"id\":42}");

    assertThat(args.stringSet(in, "s")).containsExactly("a", "b");
    assertThat(args.stringSet(in, "missing")).isEmpty();
    assertThat(args.optionalDate(in, "d")).contains(LocalDate.of(2026, 1, 31));
    assertThat(args.optionalDate(in, "missing")).isEmpty();
    assertThat(args.optionalInt(in, "n", 1, 10)).contains(7);
    assertThat(args.optionalInt(in, "missing", 1, 10)).isEmpty();
    assertThat(args.requiredLong(in, "id")).isEqualTo(42L);
  }

  @Test
  void badScalarsAreToolErrors() throws Exception {
    JsonNode in = json("{\"d\":\"31/01/2026\",\"n\":99}");

    assertThatThrownBy(() -> args.optionalDate(in, "d")).isInstanceOf(AskToolException.class);
    assertThatThrownBy(() -> args.optionalInt(in, "n", 1, 10)).isInstanceOf(AskToolException.class);
    assertThatThrownBy(() -> args.requiredLong(in, "id")).isInstanceOf(AskToolException.class);
  }
}
