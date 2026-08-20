package com.ako.dbuff.service.discord.command;

import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.resources.model.PlayerInfo;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerReferenceResolverTest {

  private static final String CHANNEL = "channel-1";

  private DbufInstanceConfigService instanceConfigService;
  private PlayerRepo playerRepo;
  private PlayerReferenceResolver resolver;

  @BeforeEach
  void setUp() {
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    playerRepo = Mockito.mock(PlayerRepo.class);

    Mockito.when(instanceConfigService.getByDiscordChannelId(CHANNEL))
        .thenReturn(
            Optional.of(
                DbufInstanceConfigResponse.builder()
                    .id("instance-1")
                    .players(
                        Set.of(
                            PlayerInfo.builder().id(204429164L).name("Пастух лолей").build(),
                            PlayerInfo.builder().id(201613150L).name("Tigress").build()))
                    .build()));
    Mockito.when(playerRepo.findByDiscordUserId(Mockito.anyString())).thenReturn(Optional.empty());
    Mockito.when(playerRepo.findByAccountIds(Mockito.anyCollection())).thenReturn(List.of());

    resolver = new PlayerReferenceResolver(instanceConfigService, playerRepo);
  }

  @Test
  void resolvesAFocusGroupNameCaseInsensitively() {
    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("tigress"));

    assertThat(result.hasUnresolved()).isFalse();
    assertThat(result.players()).hasSize(1);
    assertThat(result.players().get(0).accountId()).isEqualTo(201613150L);
    assertThat(result.players().get(0).name()).isEqualTo("Tigress");
  }

  @Test
  void resolvesACyrillicFocusGroupName() {
    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("Пастух лолей"));

    assertThat(result.players()).hasSize(1);
    assertThat(result.players().get(0).accountId()).isEqualTo(204429164L);
  }

  @Test
  void resolvesADiscordMentionViaTheLink() {
    Mockito.when(playerRepo.findByDiscordUserId("123456789"))
        .thenReturn(
            Optional.of(
                PlayerDomain.builder()
                    .id(204429164L)
                    .name("Пастух лолей")
                    .discordUserId("123456789")
                    .build()));

    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("<@123456789>"));

    assertThat(result.players()).hasSize(1);
    assertThat(result.players().get(0).accountId()).isEqualTo(204429164L);
  }

  @Test
  void resolvesTheNicknameMentionForm() {
    Mockito.when(playerRepo.findByDiscordUserId("123456789"))
        .thenReturn(Optional.of(PlayerDomain.builder().id(204429164L).name("X").build()));

    assertThat(resolver.resolve(CHANNEL, List.of("<@!123456789>")).players()).hasSize(1);
  }

  @Test
  void anUnlinkedMentionIsReportedNotDropped() {
    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("<@999>"));

    assertThat(result.players()).isEmpty();
    assertThat(result.unresolved()).containsExactly("<@999>");
  }

  @Test
  void resolvesARawAccountId() {
    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("86745912"));

    assertThat(result.players()).hasSize(1);
    assertThat(result.players().get(0).accountId()).isEqualTo(86745912L);
  }

  @Test
  void aRawAccountIdPicksUpAKnownNameWhenAvailable() {
    Mockito.when(playerRepo.findByAccountIds(List.of(86745912L)))
        .thenReturn(List.of(PlayerDomain.builder().id(86745912L).name("Dendi").build()));

    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("86745912"));

    assertThat(result.players().get(0).name()).isEqualTo("Dendi");
  }

  @Test
  void unknownNamesAreReportedRatherThanDropped() {
    PlayerReferenceResolver.Resolution result =
        resolver.resolve(CHANNEL, List.of("Tigress", "Nobody"));

    assertThat(result.players()).hasSize(1);
    assertThat(result.unresolved()).containsExactly("Nobody");
    assertThat(result.hasUnresolved()).isTrue();
  }

  @Test
  void duplicatesAreCollapsed() {
    PlayerReferenceResolver.Resolution result =
        resolver.resolve(CHANNEL, List.of("Tigress", "tigress", "201613150"));

    assertThat(result.players()).hasSize(1);
  }

  @Test
  void orderIsPreserved() {
    PlayerReferenceResolver.Resolution result =
        resolver.resolve(CHANNEL, List.of("Tigress", "Пастух лолей"));

    assertThat(result.players())
        .extracting(PlayerReferenceResolver.ResolvedPlayer::name)
        .containsExactly("Tigress", "Пастух лолей");
  }

  @Test
  void blankEntriesAreIgnoredWithoutBeingReported() {
    PlayerReferenceResolver.Resolution result =
        resolver.resolve(CHANNEL, List.of("Tigress", "", "   "));

    assertThat(result.players()).hasSize(1);
    assertThat(result.unresolved()).isEmpty();
  }

  @Test
  void unregisteredChannel_leavesEveryNameUnresolved() {
    Mockito.when(instanceConfigService.getByDiscordChannelId(CHANNEL)).thenReturn(Optional.empty());

    PlayerReferenceResolver.Resolution result = resolver.resolve(CHANNEL, List.of("Tigress"));

    assertThat(result.players()).isEmpty();
    assertThat(result.unresolved()).containsExactly("Tigress");
  }

  @Test
  void suggest_offersTheNearestTrackedName() {
    assertThat(resolver.suggest(CHANNEL, "Tigres")).contains("Tigress");
  }

  @Test
  void suggest_unrelatedInput_isEmpty() {
    assertThat(resolver.suggest(CHANNEL, "zzzzzzzzzzzz")).isEmpty();
  }
}
