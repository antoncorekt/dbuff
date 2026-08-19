package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ExternalPlayerStatisticRepository;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse;
import com.ako.dbuff.resources.model.ExternalPlayerStatisticResponse.HistoryEntry;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ExternalPlayerStatisticService}. */
@ExtendWith(MockitoExtension.class)
class ExternalPlayerStatisticServiceTest {

  private static final String PLAYER_NAME = "Dendi";
  private static final Long EXTERNAL_PLAYER_ID = 999L;
  private static final Set<Long> FOCUS_PLAYER_IDS = Set.of(1L, 2L);

  @Mock private PlayerRepo playerRepo;
  @Mock private AbilityRepo abilityRepo;
  @Mock private ExternalPlayerStatisticRepository externalPlayerStatisticRepository;
  @Mock private DbufInstanceConfigService instanceConfigService;

  @InjectMocks private ExternalPlayerStatisticService service;

  @Test
  void getStatistics_playerNotFound_returnsEmptyResponse() {
    when(playerRepo.findByName(PLAYER_NAME)).thenReturn(Optional.empty());

    ExternalPlayerStatisticResponse response = service.getStatistics(FOCUS_PLAYER_IDS, PLAYER_NAME);

    assertThat(response.getPlayerName()).isEqualTo(PLAYER_NAME);
    assertThat(response.getPlayerId()).isNull();
    assertThat(response.getHistory()).isEmpty();
    assertThat(response.getAgainstStat().getWin()).isZero();
    assertThat(response.getAgainstStat().getLose()).isZero();
    assertThat(response.getTeammateStat().getWin()).isZero();
    assertThat(response.getTeammateStat().getLose()).isZero();

    verify(externalPlayerStatisticRepository, never()).findFocusVsExternalRows(any(), anyLong());
  }

  @Test
  void getStatistics_noFocusPlayers_returnsEmptyHistoryWithPlayerId() {
    when(playerRepo.findByName(PLAYER_NAME))
        .thenReturn(
            Optional.of(PlayerDomain.builder().id(EXTERNAL_PLAYER_ID).name(PLAYER_NAME).build()));

    ExternalPlayerStatisticResponse response = service.getStatistics(Set.of(), PLAYER_NAME);

    assertThat(response.getPlayerId()).isEqualTo(EXTERNAL_PLAYER_ID);
    assertThat(response.getHistory()).isEmpty();
    verify(externalPlayerStatisticRepository, never()).findFocusVsExternalRows(any(), anyLong());
  }

  @Test
  void getStatistics_aggregatesWinsLossesTeamsAndSkills() {
    when(playerRepo.findByName(PLAYER_NAME))
        .thenReturn(
            Optional.of(PlayerDomain.builder().id(EXTERNAL_PLAYER_ID).name(PLAYER_NAME).build()));

    // Row layout: matchId, focusWin, focusRadiant, externalRadiant, externalHero, externalSlot,
    //             matchDate
    // Match 10: teammate (both radiant), focus won -> teammateWin. Appears TWICE (two focus
    //           players in the same match) and must collapse to a single history entry.
    // Match 20: against  (focus radiant, ext dire), focus lost -> againstLose
    // Match 30: against  (focus dire, ext radiant), focus won  -> againstWin
    // Dates are intentionally out of input order to verify sorting by matchDate desc.
    List<Object[]> rows =
        List.of(
            new Object[] {
              10L, 1L, Boolean.TRUE, Boolean.TRUE, "Pudge", 1L, LocalDate.of(2024, 1, 10)
            },
            new Object[] {
              10L, 1L, Boolean.TRUE, Boolean.TRUE, "Pudge", 1L, LocalDate.of(2024, 1, 10)
            },
            new Object[] {
              20L, 0L, Boolean.TRUE, Boolean.FALSE, "Invoker", 6L, LocalDate.of(2024, 3, 20)
            },
            new Object[] {
              30L, 1L, Boolean.FALSE, Boolean.TRUE, "Anti-Mage", 2L, LocalDate.of(2024, 2, 15)
            });
    when(externalPlayerStatisticRepository.findFocusVsExternalRows(
            FOCUS_PLAYER_IDS, EXTERNAL_PLAYER_ID))
        .thenReturn(rows);

    // Abilities for match 10, external at slot 1.
    when(abilityRepo.findAllByMatchId(10L))
        .thenReturn(
            List.of(
                ability(10L, 1L, "Meat Hook"),
                ability(10L, 1L, "Rot"),
                ability(10L, 5L, "Sun Strike")));
    when(abilityRepo.findAllByMatchId(20L)).thenReturn(List.of());
    when(abilityRepo.findAllByMatchId(30L)).thenReturn(List.of());

    ExternalPlayerStatisticResponse response = service.getStatistics(FOCUS_PLAYER_IDS, PLAYER_NAME);

    assertThat(response.getPlayerId()).isEqualTo(EXTERNAL_PLAYER_ID);
    // Match 10 counted once despite two rows.
    assertThat(response.getTeammateStat().getWin()).isEqualTo(1);
    assertThat(response.getTeammateStat().getLose()).isZero();
    assertThat(response.getAgainstStat().getWin()).isEqualTo(1);
    assertThat(response.getAgainstStat().getLose()).isEqualTo(1);

    // Deduped: one entry per unique match, sorted by matchDate descending (20 -> 30 -> 10).
    assertThat(response.getHistory()).hasSize(3);
    assertThat(response.getHistory().stream().map(HistoryEntry::getMatchId))
        .containsExactly(20L, 30L, 10L);
    assertThat(response.getHistory().stream().map(HistoryEntry::getMatchDate))
        .containsExactly(
            LocalDate.of(2024, 3, 20), LocalDate.of(2024, 2, 15), LocalDate.of(2024, 1, 10));

    HistoryEntry teammateEntry =
        response.getHistory().stream()
            .filter(entry -> entry.getMatchId() == 10L)
            .findFirst()
            .orElseThrow();
    assertThat(teammateEntry.isTeammate()).isTrue();
    assertThat(teammateEntry.isAgainst()).isFalse();
    assertThat(teammateEntry.isPlayerWon()).isTrue();
    assertThat(teammateEntry.getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 10));
    assertThat(teammateEntry.getDotabuffLink())
        .isEqualTo("https://www.dotabuff.com/matches/10/builds");
    assertThat(teammateEntry.getMatchStats().getPlayerHero()).isEqualTo("Pudge");
    assertThat(teammateEntry.getMatchStats().getPlayerSkills()).containsExactly("Meat Hook", "Rot");

    HistoryEntry againstEntry =
        response.getHistory().stream()
            .filter(entry -> entry.getMatchId() == 20L)
            .findFirst()
            .orElseThrow();
    assertThat(againstEntry.isAgainst()).isTrue();
    assertThat(againstEntry.isTeammate()).isFalse();
    assertThat(againstEntry.isPlayerWon()).isFalse();
    assertThat(againstEntry.getMatchStats().getPlayerSkills()).isEmpty();
  }

  @Test
  void getStatisticsForInstance_instanceNotFound_returnsEmptyResponse() {
    when(instanceConfigService.getDomainById("missing")).thenReturn(Optional.empty());

    ExternalPlayerStatisticResponse response =
        service.getStatisticsForInstance("missing", PLAYER_NAME);

    assertThat(response.getPlayerName()).isEqualTo(PLAYER_NAME);
    assertThat(response.getPlayerId()).isNull();
    assertThat(response.getHistory()).isEmpty();
    verify(playerRepo, never()).findByName(any());
  }

  @Test
  void getStatisticsByNamePattern_returnsOneResponsePerMatchedPlayer() {
    when(playerRepo.findByNameMatchingRegex(".*MIT"))
        .thenReturn(List.of(player(101L, "TERMIT"), player(102L, "HERMIT")));
    when(externalPlayerStatisticRepository.findFocusVsExternalRows(any(), anyLong()))
        .thenReturn(List.of());

    List<ExternalPlayerStatisticResponse> responses =
        service.getStatisticsByNamePattern(FOCUS_PLAYER_IDS, ".*MIT");

    assertThat(responses).hasSize(2);
    assertThat(responses.stream().map(ExternalPlayerStatisticResponse::getPlayerName))
        .containsExactlyInAnyOrder("TERMIT", "HERMIT");
    assertThat(responses.stream().map(ExternalPlayerStatisticResponse::getPlayerId))
        .containsExactlyInAnyOrder(101L, 102L);
  }

  @Test
  void getStatisticsByNamePattern_invalidRegex_returnsEmptyWithoutQuerying() {
    List<ExternalPlayerStatisticResponse> responses =
        service.getStatisticsByNamePattern(FOCUS_PLAYER_IDS, "[unclosed");

    assertThat(responses).isEmpty();
    verify(playerRepo, never()).findByNameMatchingRegex(any());
  }

  private static PlayerDomain player(Long id, String name) {
    return PlayerDomain.builder().id(id).name(name).build();
  }

  private static AbilityDomain ability(Long matchId, Long slot, String prettyName) {
    return AbilityDomain.builder().matchId(matchId).playerSlot(slot).prettyName(prettyName).build();
  }
}
