package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.service.discord.DiscordStatisticFormatter;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.ranking.ExternalPlayerStatisticService;
import com.ako.dbuff.service.ranking.ScoreboardStatisticService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ScoutCommandTest {

  private DbufInstanceConfigService instanceConfigService;
  private ExternalPlayerStatisticService externalPlayerStatisticService;
  private ScoreboardStatisticService scoreboardStatisticService;
  private ScoutCommand command;

  @BeforeEach
  void setUp() {
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    externalPlayerStatisticService = Mockito.mock(ExternalPlayerStatisticService.class);
    scoreboardStatisticService = Mockito.mock(ScoreboardStatisticService.class);
    DiscordStatisticFormatter formatter = Mockito.mock(DiscordStatisticFormatter.class);

    command =
        new ScoutCommand(
            instanceConfigService,
            externalPlayerStatisticService,
            scoreboardStatisticService,
            formatter);

    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.of(DbufInstanceConfigResponse.builder().id("instance-1").build()));
    Mockito.when(formatter.formatPlayer(Mockito.any())).thenReturn(List.of("stats line"));
    Mockito.when(
            externalPlayerStatisticService.getStatisticsByNamePatternForInstance(
                Mockito.anyString(), Mockito.anyString()))
        .thenReturn(List.of(opponent("Termit")));
    Mockito.when(
            scoreboardStatisticService.getStatisticsForInstance(
                Mockito.anyString(), Mockito.any(byte[].class)))
        .thenReturn(List.of(opponent("Termit")));
  }

  private static ExternalPlayerStatisticResponse opponent(String name) {
    return ExternalPlayerStatisticResponse.builder().playerName(name).build();
  }

  @Test
  void definitionExposesPlayerAndScoreboard() {
    assertThat(command.getDefinition().getSubcommands())
        .extracting(subcommand -> subcommand.getName())
        .containsExactlyInAnyOrder("player", "scoreboard");
  }

  @Test
  void keepsTheLegacyVsAlias() {
    assertThat(command.getTextAliases()).containsExactly("vs");
  }

  @Test
  void unregisteredChannel_repliesEphemerallyAndDoesNoWork() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());
    FakeCommandContext context = FakeCommandContext.builder().option("name", "Termit").build();

    command.execute("player", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(externalPlayerStatisticService);
  }

  @Test
  void player_missingName_repliesEphemerally() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("player", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void player_postsTheFormattedStatistics() {
    FakeCommandContext context = FakeCommandContext.builder().option("name", "Termit").build();

    command.execute("player", context);

    assertThat(context.getAcknowledgeSummary()).contains("Termit");
    assertThat(context.getPosts()).contains("stats line");
  }

  @Test
  void player_noMatches_saysSo() {
    Mockito.when(
            externalPlayerStatisticService.getStatisticsByNamePatternForInstance(
                Mockito.anyString(), Mockito.anyString()))
        .thenReturn(List.of());
    FakeCommandContext context = FakeCommandContext.builder().option("name", "Nobody").build();

    command.execute("player", context);

    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("No players"));
  }

  @Test
  void player_severalMatches_announcesTheCountFirst() {
    Mockito.when(
            externalPlayerStatisticService.getStatisticsByNamePatternForInstance(
                Mockito.anyString(), Mockito.anyString()))
        .thenReturn(List.of(opponent("Termit"), opponent("Termite")));
    FakeCommandContext context = FakeCommandContext.builder().option("name", "Termit").build();

    command.execute("player", context);

    assertThat(context.getPosts().get(0)).contains("2");
  }

  /**
   * The regression that matters for the migration: the legacy text form and the slash form must
   * reach the same handler with the same resolved option.
   */
  @Test
  void legacyVsForm_mapsItsWholeArgumentStringToTheNameOption() {
    Map<String, String> options = command.parseTextArguments("vs", "Termit", "");

    assertThat(options).containsEntry("name", "Termit");
    assertThat(command.resolveTextSubcommand("vs", "Termit")).isEqualTo("player");
  }

  @Test
  void legacyVsForm_keepsAMultiWordNameWhole() {
    Map<String, String> options = command.parseTextArguments("vs", "Some", "Long Name");

    assertThat(options).containsEntry("name", "Some Long Name");
  }

  @Test
  void legacyVsForm_withNoArgument_yieldsNoOptionsSoTheHandlerAsksForOne() {
    assertThat(command.parseTextArguments("vs", null, "")).isEmpty();
  }

  @Test
  void scoreboard_readsTheAttachmentAndPostsOpponents() {
    FakeCommandContext context =
        FakeCommandContext.builder().attachment(new byte[] {1, 2, 3}).build();

    command.execute("scoreboard", context);

    assertThat(context.getAcknowledgeSummary()).contains("Reading scoreboard");
    assertThat(context.getPosts()).contains("stats line");
  }

  @Test
  void scoreboard_noOpponentsDetected_saysSo() {
    Mockito.when(
            scoreboardStatisticService.getStatisticsForInstance(
                Mockito.anyString(), Mockito.any(byte[].class)))
        .thenReturn(List.of());
    FakeCommandContext context =
        FakeCommandContext.builder().attachment(new byte[] {1, 2, 3}).build();

    command.execute("scoreboard", context);

    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("No opponents"));
  }

  @Test
  void scoreboard_missingAttachment_failsInTheThreadRatherThanCallingVision() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("scoreboard", context);

    assertThat(context.getFailures()).hasSize(1);
    Mockito.verifyNoInteractions(scoreboardStatisticService);
  }
}
