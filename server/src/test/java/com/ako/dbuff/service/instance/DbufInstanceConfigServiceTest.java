package com.ako.dbuff.service.instance;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.replay.report.ReplayReportProperties;
import com.ako.dbuff.service.replay.report.SectionSwitch;
import com.ako.dbuff.service.replay.report.TestSections;
import com.ako.dbuff.service.scheduler.InstanceSchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbufInstanceConfigServiceTest {

  private static final long PLAYER_ID = 204429164L;

  private DbufInstanceConfigRepository repository;
  private InstanceSchedulerService scheduler;
  private PlayerRepo playerRepo;
  private PlayersApi playersApi;
  private DbufInstanceConfigService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(DbufInstanceConfigRepository.class);
    scheduler = Mockito.mock(InstanceSchedulerService.class);
    playerRepo = Mockito.mock(PlayerRepo.class);
    playersApi = Mockito.mock(PlayersApi.class);
    Mockito.when(playerRepo.findByAccountIds(List.of(PLAYER_ID)))
        .thenReturn(List.of(PlayerDomain.builder().id(PLAYER_ID).name("Tigress").build()));
    Mockito.when(playerRepo.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
    Mockito.when(repository.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));

    service =
        new DbufInstanceConfigService(
            repository,
            playerRepo,
            playersApi,
            Mockito.mock(ConstantsManagers.class),
            new ObjectMapper(),
            scheduler,
            new InstanceSettingsApplier(
                Mockito.mock(GameModeResolver.class),
                new SectionSwitch(
                    new ReplayReportProperties(),
                    List.of(TestSections.named("lanes", 20, false)))));
  }

  private static RegisterInstanceRequest request() {
    return RegisterInstanceRequest.builder()
        .playerIds(Set.of(PLAYER_ID))
        .discordChannelId("channel-1")
        .discordGuildId("guild-1")
        .build();
  }

  private static DbufInstanceConfigDomain existing(boolean active) {
    return DbufInstanceConfigDomain.builder()
        .id("instance-1")
        .discordChannelId("channel-1")
        .active(active)
        .build();
  }

  @Test
  void register_activeChannel_isRejected() {
    Mockito.when(repository.findByDiscordChannelId("channel-1"))
        .thenReturn(Optional.of(existing(true)));

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already has a registered instance");
    Mockito.verify(scheduler, Mockito.never()).scheduleJobsForInstance(Mockito.any());
  }

  @Test
  void register_deactivatedChannel_isReactivatedAndRescheduled() {
    DbufInstanceConfigDomain inactive = existing(false);
    Mockito.when(repository.findByDiscordChannelId("channel-1")).thenReturn(Optional.of(inactive));

    var response = service.register(request());

    assertThat(response.getId()).isEqualTo("instance-1");
    assertThat(response.getActive()).isTrue();
    assertThat(inactive.getPlayerIds()).containsExactly(PLAYER_ID);
    Mockito.verify(scheduler).scheduleJobsForInstance("instance-1");
  }

  @Test
  void update_activatingAnInactiveInstance_reschedulesItsJobs() {
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(existing(false)));

    service.update("instance-1", UpdateInstanceRequest.builder().active(true).build());

    Mockito.verify(scheduler).scheduleJobsForInstance("instance-1");
  }

  @Test
  void deactivateAllForGuild_deactivatesEveryActiveInstanceOfThatGuild() {
    DbufInstanceConfigDomain first = existing(true);
    DbufInstanceConfigDomain second = existing(true);
    second.setId("instance-2");
    Mockito.when(repository.findByDiscordGuildIdAndActiveTrue("guild-1"))
        .thenReturn(List.of(first, second));
    Mockito.when(repository.findById(Mockito.anyString()))
        .thenAnswer(call -> Optional.of(call.getArgument(0).equals("instance-1") ? first : second));

    int count = service.deactivateAllForGuild("guild-1");

    assertThat(count).isEqualTo(2);
    assertThat(first.getActive()).isFalse();
    assertThat(second.getActive()).isFalse();
    Mockito.verify(scheduler).unscheduleJobsForInstance("instance-1");
    Mockito.verify(scheduler).unscheduleJobsForInstance("instance-2");
  }

  private void stubApiProfile(long accountId, String persona) {
    var response = new com.ako.dbuff.dotapi.model.PlayersResponse();
    if (persona != null) {
      var profile = new com.ako.dbuff.dotapi.model.PlayersResponseProfile();
      profile.setPersonaname(persona);
      response.setProfile(profile);
    }
    try {
      Mockito.when(playersApi.getPlayersByAccountId(accountId)).thenReturn(response);
    } catch (com.ako.dbuff.dotapi.invoker.ApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private RegisterInstanceRequest requestFor(long accountId) {
    return RegisterInstanceRequest.builder()
        .playerIds(Set.of(accountId))
        .discordChannelId("channel-2")
        .build();
  }

  @Test
  void register_sameNameDifferentAccount_isStoredSeparatelyNotRepointed() {
    PlayerDomain other = PlayerDomain.builder().id(111L).name("Pudge").build();
    stubApiProfile(222L, "Pudge");
    Mockito.when(playerRepo.findByName("Pudge")).thenReturn(Optional.of(other));

    var response = service.register(requestFor(222L));

    assertThat(other.getId()).isEqualTo(111L);
    assertThat(response.getPlayers())
        .extracting(info -> info.getName() + ":" + info.getId())
        .containsExactly("Pudge (222):222");
  }

  @Test
  void register_sameNameWithNoAccountYet_adoptsTheAccount() {
    PlayerDomain legacy = PlayerDomain.builder().id(null).name("Pudge").build();
    stubApiProfile(222L, "Pudge");
    Mockito.when(playerRepo.findByName("Pudge")).thenReturn(Optional.of(legacy));

    service.register(requestFor(222L));

    assertThat(legacy.getId()).isEqualTo(222L);
  }

  @Test
  void register_accountWithoutPersona_getsAPlaceholderName() {
    stubApiProfile(333L, null);
    Mockito.when(playerRepo.findByName(Mockito.anyString())).thenReturn(Optional.empty());

    var response = service.register(requestFor(333L));

    assertThat(response.getPlayers())
        .extracting(info -> info.getName())
        .containsExactly("Player_333");
  }

  @Test
  void register_startsTrackingNow() {
    var before = java.time.LocalDateTime.now();
    var captor = org.mockito.ArgumentCaptor.forClass(DbufInstanceConfigDomain.class);

    service.register(request());

    Mockito.verify(repository).save(captor.capture());
    assertThat(captor.getValue().getTrackingSince()).isAfterOrEqualTo(before);
  }

  @Test
  void update_addingAnAlreadyTrackedPlayer_changesNoCutoff() {
    DbufInstanceConfigDomain instance = existing(true);
    instance.getPlayers().add(PlayerDomain.builder().id(PLAYER_ID).name("Tigress").build());
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    service.update(
        "instance-1", UpdateInstanceRequest.builder().addPlayerIds(Set.of(PLAYER_ID)).build());

    assertThat(instance.getTrackingSince()).isNull();
    assertThat(instance.getPlayerTrackingSince()).isEmpty();
  }

  @Test
  void update_addingANewPlayer_setsOnlyThatPlayersCutoff() {
    DbufInstanceConfigDomain instance = existing(true);
    stubApiProfile(555L, "Newcomer");
    Mockito.when(playerRepo.findByName("Newcomer")).thenReturn(Optional.empty());
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    service.update(
        "instance-1", UpdateInstanceRequest.builder().addPlayerIds(Set.of(555L)).build());

    assertThat(instance.getTrackingSince()).isNull();
    assertThat(instance.getPlayerTrackingSince()).containsOnlyKeys(555L);
    assertThat(instance.trackingSinceFor(555L)).isNotNull();
    assertThat(instance.trackingSinceFor(PLAYER_ID)).isNull();
  }

  @Test
  void update_removingAPlayer_dropsTheirCutoff() {
    DbufInstanceConfigDomain instance = existing(true);
    instance.getPlayers().add(PlayerDomain.builder().id(PLAYER_ID).name("Tigress").build());
    instance.getPlayerTrackingSince().put(PLAYER_ID, java.time.LocalDateTime.now());
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    service.update(
        "instance-1", UpdateInstanceRequest.builder().removePlayerIds(Set.of(PLAYER_ID)).build());

    assertThat(instance.getPlayerTrackingSince()).isEmpty();
  }

  @Test
  void update_reactivating_restartsTheWindowForEveryone() {
    DbufInstanceConfigDomain instance = existing(false);
    instance.getPlayerTrackingSince().put(PLAYER_ID, java.time.LocalDateTime.now().minusDays(3));
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    service.update("instance-1", UpdateInstanceRequest.builder().active(true).build());

    assertThat(instance.getTrackingSince()).isNotNull();
    assertThat(instance.getPlayerTrackingSince()).isEmpty();
  }

  @Test
  void update_notAddingAPlayer_leavesTrackingSinceAlone() {
    DbufInstanceConfigDomain instance = existing(true);
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    service.update("instance-1", UpdateInstanceRequest.builder().name("Squad").build());

    assertThat(instance.getTrackingSince()).isNull();
  }

  @Test
  void update_settings_areStoredAndReflectedInTheResponse() {
    DbufInstanceConfigDomain instance = existing(true);
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    var response =
        service.update(
            "instance-1",
            UpdateInstanceRequest.builder()
                .aiSummaryEnabled(false)
                .reportDetail("standard")
                .quietHours("22:00-07:00")
                .timezone("Europe/Kyiv")
                .build());

    assertThat(instance.effectiveSettings().isAiSummaryOn()).isFalse();
    assertThat(response.getSettings().getReportDetail()).isEqualTo("STANDARD");
    assertThat(response.getSettings().getQuietHours()).isEqualTo("22:00-07:00");
    assertThat(response.getSettings().getTimezone()).isEqualTo("Europe/Kyiv");
  }

  @Test
  void update_invalidSetting_failsAndSavesNothing() {
    DbufInstanceConfigDomain instance = existing(true);
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    assertThatThrownBy(
            () ->
                service.update(
                    "instance-1", UpdateInstanceRequest.builder().reportDetail("loud").build()))
        .isInstanceOf(IllegalArgumentException.class);
    Mockito.verify(repository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aChannelThatNeverChoseAnything_reportsTheDefaults() {
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(existing(true)));

    var settings = service.getById("instance-1").orElseThrow().getSettings();

    assertThat(settings.isAiSummaryEnabled()).isTrue();
    assertThat(settings.getReportDetail()).isEqualTo("FULL");
    assertThat(settings.getQuietHours()).isEqualTo("server default");
    assertThat(settings.getTimezone()).isEqualTo("server default");
    assertThat(settings.getDefaultGameModes()).isEmpty();
  }

  @Test
  void update_sectionsAndLanguage_areStoredAndReflectedInTheResponse() {
    DbufInstanceConfigDomain instance = existing(true);
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(instance));

    var response =
        service.update(
            "instance-1",
            UpdateInstanceRequest.builder().sections("Lanes").language("Russian").build());

    assertThat(instance.effectiveSettings().getSections()).isEqualTo("lanes");
    assertThat(instance.effectiveSettings().getLanguage()).isEqualTo("Russian");
    assertThat(response.getSettings().getSections()).isEqualTo("lanes");
    assertThat(response.getSettings().getLanguage()).isEqualTo("Russian");
  }

  @Test
  void update_unknownSection_failsAndSavesNothing() {
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(existing(true)));

    assertThatThrownBy(
            () ->
                service.update(
                    "instance-1", UpdateInstanceRequest.builder().sections("nope").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lanes");
    Mockito.verify(repository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aChannelThatNeverChoseSectionsOrLanguage_reportsTheServerDefaults() {
    Mockito.when(repository.findById("instance-1")).thenReturn(Optional.of(existing(true)));

    var settings = service.getById("instance-1").orElseThrow().getSettings();

    assertThat(settings.getSections()).isEqualTo("server default");
    assertThat(settings.getLanguage()).isEqualTo("server default");
  }
}
