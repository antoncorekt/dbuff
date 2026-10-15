package com.ako.dbuff.service.discord.command.impl;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.discord.ChannelReadinessService;
import com.ako.dbuff.service.discord.command.CommandRegistry;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class DbuffConfigCommandTest {

  private static final Long TIGRESS_ID = 201613150L;

  private DbufInstanceConfigService instanceConfigService;
  private PlayerReferenceResolver playerResolver;
  private PlayerRepo playerRepo;
  private ChannelReadinessService channelReadiness;
  private DbuffConfigCommand command;

  @BeforeEach
  void setUp() {
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    playerResolver = Mockito.mock(PlayerReferenceResolver.class);
    playerRepo = Mockito.mock(PlayerRepo.class);
    channelReadiness = Mockito.mock(ChannelReadinessService.class);
    CommandRegistry registry = Mockito.mock(CommandRegistry.class);

    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.of(instance()));
    Mockito.when(instanceConfigService.update(Mockito.anyString(), Mockito.any()))
        .thenReturn(instance());
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(List.of(), List.of()));
    Mockito.when(playerRepo.findByAccountIds(Mockito.anyCollection())).thenReturn(List.of());

    Mockito.when(channelReadiness.findProblems(Mockito.anyString())).thenReturn(List.of());

    command =
        new DbuffConfigCommand(
            instanceConfigService, playerResolver, playerRepo, channelReadiness, registry);
  }

  private static DbufInstanceConfigResponse instance() {
    return DbufInstanceConfigResponse.builder()
        .id("instance-1")
        .active(true)
        .players(Set.of(PlayerInfo.builder().id(TIGRESS_ID).name("Tigress").build()))
        .gameModes(Set.of())
        .settings(
            com.ako.dbuff.resources.model.InstanceSettingsResponse.from(
                new com.ako.dbuff.dao.model.InstanceSettings()))
        .build();
  }

  @Test
  void definitionExposesEverySubcommand() {
    assertThat(command.getDefinition().getSubcommands())
        .extracting(subcommand -> subcommand.getName())
        .containsExactlyInAnyOrder(
            "register",
            "status",
            "add",
            "remove",
            "link",
            "modes",
            "settings",
            "deactivate",
            "help");
  }

  @Test
  void keepsTheLegacyDbufTextAlias() {
    assertThat(command.getTextAliases()).containsExactly("dbuf");
  }

  @Test
  void status_unregisteredChannel_repliesEphemerallyAndDoesNothingElse() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("status", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff register");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void status_registeredChannel_acknowledgesAndPostsAnEmbed() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("status", context);

    assertThat(context.getAcknowledgeSummary()).isNotNull();
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void register_forwardsTheNumericIdAndChannelScope() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "204429164")
            .option("name", "Squad")
            .parentChannelId("channel-7")
            .guildId("guild-9")
            .build();
    Mockito.when(instanceConfigService.register(Mockito.any())).thenReturn(instance());

    command.execute("register", context);

    ArgumentCaptor<RegisterInstanceRequest> captor =
        ArgumentCaptor.forClass(RegisterInstanceRequest.class);
    Mockito.verify(instanceConfigService).register(captor.capture());

    assertThat(captor.getValue().getPlayerIds()).containsExactly(204429164L);
    assertThat(captor.getValue().getDiscordChannelId()).isEqualTo("channel-7");
    assertThat(captor.getValue().getDiscordGuildId()).isEqualTo("guild-9");
    assertThat(captor.getValue().getName()).isEqualTo("Squad");
  }

  @Test
  void register_acceptsSteam64AndProfileLinks() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "76561198164694892,https://www.dotabuff.com/players/279195408")
            .build();
    Mockito.when(instanceConfigService.register(Mockito.any())).thenReturn(instance());

    command.execute("register", context);

    ArgumentCaptor<RegisterInstanceRequest> captor =
        ArgumentCaptor.forClass(RegisterInstanceRequest.class);
    Mockito.verify(instanceConfigService).register(captor.capture());
    assertThat(captor.getValue().getPlayerIds()).containsExactly(204429164L, 279195408L);
  }

  @Test
  void register_oneUnreadablePlayerAmongValidOnes_rejectsAndNamesIt() {
    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "204429164,Termit").build();

    command.execute("register", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("`Termit`").contains("Steam64");
    Mockito.verify(instanceConfigService, Mockito.never()).register(Mockito.any());
  }

  @Test
  void register_embedExplainsTheNextSteps() {
    FakeCommandContext context = FakeCommandContext.builder().option("player", "204429164").build();
    Mockito.when(instanceConfigService.register(Mockito.any())).thenReturn(instance());

    command.execute("register", context);

    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEmbeds().get(0).getFields())
        .anyMatch(field -> "Next steps".equals(field.getName()));
  }

  @Test
  void register_nonNumericPlayer_isRejectedBeforeAnyWork() {
    FakeCommandContext context = FakeCommandContext.builder().option("player", "Termit").build();

    command.execute("register", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verify(instanceConfigService, Mockito.never()).register(Mockito.any());
  }

  @Test
  void register_channelNotReady_listsTheProblemsBeforeAnyWork() {
    Mockito.when(channelReadiness.findProblems("channel-1"))
        .thenReturn(
            List.of("The bot is missing permissions in this channel: Create Public Threads"));
    FakeCommandContext context = FakeCommandContext.builder().option("player", "204429164").build();

    command.execute("register", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Create Public Threads");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verify(instanceConfigService, Mockito.never()).register(Mockito.any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"register", "add", "remove", "link", "modes", "deactivate"})
  void configChanges_withoutManagePermission_areRefusedPrivately(String subcommand) {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .canManageChannel(false)
            .option("player", "204429164")
            .option("mode", "all")
            .build();

    command.execute(subcommand, context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getEphemeralReplies().get(0)).contains("Manage Channel");
    assertThat(context.getAcknowledgeSummary()).isNull();
    Mockito.verifyNoInteractions(playerRepo);
    Mockito.verify(instanceConfigService, Mockito.never()).register(Mockito.any());
    Mockito.verify(instanceConfigService, Mockito.never()).update(Mockito.any(), Mockito.any());
    Mockito.verify(instanceConfigService, Mockito.never()).deactivate(Mockito.any());
  }

  @Test
  void nullSubcommand_fallsBackToHelpEvenWithoutManagePermission() {
    FakeCommandContext context = FakeCommandContext.builder().canManageChannel(false).build();

    command.execute(null, context);

    assertThat(context.getEphemeralReplies()).isEmpty();
    assertThat(context.getAcknowledgeSummary()).isNotNull();
  }

  @Test
  void settings_changesAreForwardedToTheService() {
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("ai_summary", "false")
            .option("replay_report", "off")
            .option("detail", "minimal")
            .option("quiet_hours", "22:00-07:00")
            .option("timezone", "Europe/Kyiv")
            .option("game_mode", "ability_draft,all_pick")
            .option("sections", "lanes,swing")
            .option("language", "Russian")
            .build();

    command.execute("settings", context);

    ArgumentCaptor<UpdateInstanceRequest> captor =
        ArgumentCaptor.forClass(UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService).update(Mockito.eq("instance-1"), captor.capture());
    UpdateInstanceRequest request = captor.getValue();
    assertThat(request.getAiSummaryEnabled()).isFalse();
    assertThat(request.getReplayReportEnabled()).isFalse();
    assertThat(request.getReportDetail()).isEqualTo("minimal");
    assertThat(request.getQuietHours()).isEqualTo("22:00-07:00");
    assertThat(request.getTimezone()).isEqualTo("Europe/Kyiv");
    assertThat(request.getDefaultGameModes()).containsExactly("ability_draft", "all_pick");
    assertThat(request.getSections()).isEqualTo("lanes,swing");
    assertThat(request.getLanguage()).isEqualTo("Russian");
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void settings_rejectedValue_isShownPrivatelyBeforeAnyThread() {
    Mockito.when(instanceConfigService.update(Mockito.anyString(), Mockito.any()))
        .thenThrow(new IllegalArgumentException("Unknown timezone `Mars/Base`."));
    FakeCommandContext context =
        FakeCommandContext.builder().option("timezone", "Mars/Base").build();

    command.execute("settings", context);

    assertThat(context.getEphemeralReplies()).containsExactly("❌ Unknown timezone `Mars/Base`.");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void settings_withNoOptions_showsTheCurrentSettingsWithoutChangingAnything() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("settings", context);

    Mockito.verify(instanceConfigService, Mockito.never())
        .update(Mockito.anyString(), Mockito.any());
    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEmbeds().get(0).getDescription()).contains("Report detail");
  }

  @Test
  void settings_changeWithoutManagePermission_isRefused() {
    FakeCommandContext context =
        FakeCommandContext.builder().canManageChannel(false).option("detail", "full").build();

    command.execute("settings", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Manage Channel");
    Mockito.verify(instanceConfigService, Mockito.never())
        .update(Mockito.anyString(), Mockito.any());
  }

  @Test
  void settings_viewingWithoutManagePermission_isAllowed() {
    FakeCommandContext context = FakeCommandContext.builder().canManageChannel(false).build();

    command.execute("settings", context);

    assertThat(context.getEmbeds()).hasSize(1);
    assertThat(context.getEphemeralReplies()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "ON", "yes", "1"})
  void settings_aiSummary_acceptsEveryWayOfSayingYes(String raw) {
    FakeCommandContext context = FakeCommandContext.builder().option("ai_summary", raw).build();

    command.execute("settings", context);

    ArgumentCaptor<UpdateInstanceRequest> captor =
        ArgumentCaptor.forClass(UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService).update(Mockito.anyString(), captor.capture());
    assertThat(captor.getValue().getAiSummaryEnabled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "OFF", "no", "0"})
  void settings_aiSummary_acceptsEveryWayOfSayingNo(String raw) {
    FakeCommandContext context = FakeCommandContext.builder().option("ai_summary", raw).build();

    command.execute("settings", context);

    ArgumentCaptor<UpdateInstanceRequest> captor =
        ArgumentCaptor.forClass(UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService).update(Mockito.anyString(), captor.capture());
    assertThat(captor.getValue().getAiSummaryEnabled()).isFalse();
  }

  @Test
  void settings_aiSummary_unreadableWord_isRejectedNotTreatedAsFalse() {
    FakeCommandContext context = FakeCommandContext.builder().option("ai_summary", "maybe").build();

    command.execute("settings", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("`maybe`");
    Mockito.verify(instanceConfigService, Mockito.never())
        .update(Mockito.anyString(), Mockito.any());
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void settings_unregisteredChannel_pointsAtRegister() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(Mockito.anyString()))
        .thenReturn(Optional.empty());
    FakeCommandContext context = FakeCommandContext.builder().option("detail", "full").build();

    command.execute("settings", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("/dbuff register");
  }

  @Test
  void status_includesTheSettings() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("status", context);

    assertThat(context.getEmbeds().get(0).getFields())
        .anyMatch(field -> "Settings".equals(field.getName()));
  }

  @Test
  void status_withoutManagePermission_stillWorks() {
    FakeCommandContext context = FakeCommandContext.builder().canManageChannel(false).build();

    command.execute("status", context);

    assertThat(context.getAcknowledgeSummary()).isNotNull();
    assertThat(context.getEmbeds()).hasSize(1);
  }

  @Test
  void status_surfacesReadinessProblemsAsWarnings() {
    Mockito.when(channelReadiness.findProblems("channel-1")).thenReturn(List.of("cannot post"));
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("status", context);

    assertThat(context.getPosts()).containsExactly("⚠️ cannot post");
  }

  @Test
  void register_alreadyRegistered_saysSoWithoutCreatingAThread() {
    Mockito.when(instanceConfigService.register(Mockito.any()))
        .thenThrow(new IllegalStateException("exists"));
    FakeCommandContext context = FakeCommandContext.builder().option("player", "204429164").build();

    command.execute("register", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("already has a registered instance");
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void add_forwardsTheIdToTheService() {
    FakeCommandContext context = FakeCommandContext.builder().option("player", "279195408").build();

    command.execute("add", context);

    ArgumentCaptor<UpdateInstanceRequest> captor =
        ArgumentCaptor.forClass(UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService).update(Mockito.eq("instance-1"), captor.capture());

    assertThat(captor.getValue().getAddPlayerIds()).containsExactly(279195408L);
  }

  @Test
  void add_withAUserOption_alsoWritesTheDiscordLink() {
    Mockito.when(playerRepo.findByAccountIds(List.of(279195408L)))
        .thenReturn(List.of(PlayerDomain.builder().id(279195408L).name("Доктор Сливси").build()));
    FakeCommandContext context =
        FakeCommandContext.builder()
            .option("player", "279195408")
            .option("user", "555000111")
            .build();

    command.execute("add", context);

    ArgumentCaptor<PlayerDomain> saved = ArgumentCaptor.forClass(PlayerDomain.class);
    Mockito.verify(playerRepo).save(saved.capture());

    assertThat(saved.getValue().getDiscordUserId()).isEqualTo("555000111");
    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("Linked"));
  }

  @Test
  void link_setsDiscordUserIdOnTheResolvedPlayer() {
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(
            new PlayerReferenceResolver.Resolution(
                List.of(new PlayerReferenceResolver.ResolvedPlayer(TIGRESS_ID, "Tigress")),
                List.of()));
    Mockito.when(playerRepo.findByAccountIds(List.of(TIGRESS_ID)))
        .thenReturn(List.of(PlayerDomain.builder().id(TIGRESS_ID).name("Tigress").build()));

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Tigress").option("user", "777").build();

    command.execute("link", context);

    ArgumentCaptor<PlayerDomain> saved = ArgumentCaptor.forClass(PlayerDomain.class);
    Mockito.verify(playerRepo).save(saved.capture());
    assertThat(saved.getValue().getDiscordUserId()).isEqualTo("777");
  }

  @Test
  void link_playerNotInTheDatabaseYet_warnsInsteadOfFailingSilently() {
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(
            new PlayerReferenceResolver.Resolution(
                List.of(new PlayerReferenceResolver.ResolvedPlayer(999L, "Ghost")), List.of()));
    Mockito.when(playerRepo.findByAccountIds(List.of(999L))).thenReturn(List.of());

    FakeCommandContext context =
        FakeCommandContext.builder().option("player", "Ghost").option("user", "777").build();

    command.execute("link", context);

    assertThat(context.getPosts())
        .anySatisfy(post -> assertThat(post).contains("not in the database"));
    Mockito.verify(playerRepo, Mockito.never()).save(Mockito.any());
  }

  @Test
  void link_withoutAUser_isRejected() {
    FakeCommandContext context = FakeCommandContext.builder().option("player", "Tigress").build();

    command.execute("link", context);

    assertThat(context.getEphemeralReplies()).hasSize(1);
    assertThat(context.getAcknowledgeSummary()).isNull();
  }

  @Test
  void remove_unresolvedPlayer_reportsItWithASuggestion() {
    Mockito.when(playerResolver.resolve(Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new PlayerReferenceResolver.Resolution(List.of(), List.of("Tigres")));
    Mockito.when(playerResolver.suggest(Mockito.anyString(), Mockito.eq("Tigres")))
        .thenReturn(Optional.of("Tigress"));

    FakeCommandContext context = FakeCommandContext.builder().option("player", "Tigres").build();

    command.execute("remove", context);

    assertThat(context.getEphemeralReplies().get(0)).contains("Tigres").contains("Tigress");
    Mockito.verify(instanceConfigService, Mockito.never()).update(Mockito.any(), Mockito.any());
  }

  @Test
  void modes_addsByDefaultAndRemovesWhenAsked() {
    command.execute("modes", FakeCommandContext.builder().option("mode", "22").build());
    command.execute(
        "modes",
        FakeCommandContext.builder().option("mode", "22").option("remove", "true").build());

    ArgumentCaptor<UpdateInstanceRequest> captor =
        ArgumentCaptor.forClass(UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService, Mockito.times(2))
        .update(Mockito.anyString(), captor.capture());

    assertThat(captor.getAllValues().get(0).getAddGameModes()).containsExactly("22");
    assertThat(captor.getAllValues().get(1).getRemoveGameModes()).containsExactly("22");
  }

  @Test
  void deactivate_callsTheServiceAndConfirms() {
    FakeCommandContext context = FakeCommandContext.builder().build();

    command.execute("deactivate", context);

    Mockito.verify(instanceConfigService).deactivate("instance-1");
    assertThat(context.getPosts()).anySatisfy(post -> assertThat(post).contains("deactivated"));
  }

  @Test
  void legacyRegisterForm_mapsPlayersAndFlags() {
    Map<String, String> options =
        command.parseTextArguments("dbuf", "register", "111 222 --modes 22 --name Squad");

    assertThat(options.get("player")).isEqualTo("111,222");
    assertThat(options.get("mode")).isEqualTo("22");
    assertThat(options.get("name")).isEqualTo("Squad");
  }

  @Test
  void legacyAddPlayersForm_mapsToTheCommaSeparatedPlayerOption() {
    Map<String, String> options =
        command.parseTextArguments("dbuf", "add-players", "86745912 45803372");

    assertThat(options.get("player")).isEqualTo("86745912,45803372");
  }

  @Test
  void legacyMultiWordNameFlagIsKeptWhole() {
    Map<String, String> options =
        command.parseTextArguments("dbuf", "register", "111 --name The Best Squad");

    assertThat(options.get("name")).isEqualTo("The Best Squad");
  }

  @Test
  void legacyStatusForm_hasNoOptions() {
    assertThat(command.parseTextArguments("dbuf", "status", "")).isEmpty();
  }
}
