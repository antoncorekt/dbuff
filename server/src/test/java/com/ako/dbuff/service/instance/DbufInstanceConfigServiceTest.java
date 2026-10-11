package com.ako.dbuff.service.instance;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.DbufInstanceConfigRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.resources.model.RegisterInstanceRequest;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.constant.ConstantsManagers;
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
  private DbufInstanceConfigService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(DbufInstanceConfigRepository.class);
    scheduler = Mockito.mock(InstanceSchedulerService.class);
    PlayerRepo playerRepo = Mockito.mock(PlayerRepo.class);
    Mockito.when(playerRepo.findAll())
        .thenReturn(List.of(PlayerDomain.builder().id(PLAYER_ID).name("Tigress").build()));
    Mockito.when(repository.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));

    service =
        new DbufInstanceConfigService(
            repository,
            playerRepo,
            Mockito.mock(PlayersApi.class),
            Mockito.mock(ConstantsManagers.class),
            new ObjectMapper(),
            scheduler);
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
}
