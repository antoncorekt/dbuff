package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPickStatAnalyzerTest {
  private static final long MATCH = 77L;
  private static final long START = 1_760_000_000L;

  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final PlayerGameStatisticRepo playerRepo = mock(PlayerGameStatisticRepo.class);
  private final AbilityRepo abilityRepo = mock(AbilityRepo.class);
  private final SkillStatCalculator calculator = mock(SkillStatCalculator.class);
  private final SkillPickStatStore store = mock(SkillPickStatStore.class);
  private final SkillStatProperties properties = new SkillStatProperties();
  private final SkillPickStatAnalyzer analyzer =
      new SkillPickStatAnalyzer(matchRepo, playerRepo, abilityRepo, calculator, store, properties);

  private static AbilityDomain ability(long slot, long id) {
    return AbilityDomain.builder().matchId(MATCH).playerSlot(slot).abilityId(id).build();
  }

  private static PlayerMatchStatisticDomain player(long slot, long hero) {
    PlayerMatchStatisticDomain p = new PlayerMatchStatisticDomain();
    p.setMatchId(MATCH);
    p.setPlayerSlot(slot);
    p.setHeroId(hero);
    return p;
  }

  @Test
  void onlyAbilityDraftIsSupported() {
    assertThat(analyzer.supports(new AbilityDraftProfile())).isTrue();
    assertThat(analyzer.supports(new AllPickProfile())).isFalse();
  }

  @Test
  void eachPlayerGetsRowsForTheWindowBeforeTheMatchAndEarlierRowsAreReplaced() {
    properties.setWindowMonths(6);
    when(matchRepo.findById(MATCH))
        .thenReturn(Optional.of(MatchDomain.builder().id(MATCH).startTime(START).build()));
    when(abilityRepo.findAllByMatchId(MATCH))
        .thenReturn(List.of(ability(0, 1), ability(0, 2), ability(128, 3)));
    when(playerRepo.findAllByMatchId(MATCH)).thenReturn(List.of(player(0, 10), player(128, 20)));
    SkillPickStatDomain row = SkillPickStatDomain.builder().matchId(MATCH).build();
    when(calculator.forPlayer(anyLong(), anyLong(), any(), anyIterable(), any(), anyInt()))
        .thenReturn(List.of(row));

    analyzer.analyze(MATCH);

    ArgumentCaptor<SkillWindow> window = ArgumentCaptor.forClass(SkillWindow.class);
    verify(calculator)
        .forPlayer(eq(MATCH), eq(0L), eq(10L), eq(List.of(1L, 2L)), window.capture(), eq(1));
    verify(calculator).forPlayer(eq(MATCH), eq(128L), eq(20L), eq(List.of(3L)), any(), eq(1));
    assertThat(window.getValue().toEpoch()).isEqualTo(START);
    assertThat(window.getValue().excludedMatchId()).isEqualTo(MATCH);
    assertThat(window.getValue().fromEpoch())
        .isEqualTo(
            Instant.ofEpochSecond(START).atZone(ZoneOffset.UTC).minusMonths(6).toEpochSecond());
    verify(store).replace(MATCH, List.of(row, row));
  }

  @Test
  void aMatchWithNoStartTimeOrNoAbilitiesStoresNothing() {
    when(matchRepo.findById(MATCH))
        .thenReturn(Optional.of(MatchDomain.builder().id(MATCH).build()));

    analyzer.analyze(MATCH);

    when(matchRepo.findById(MATCH))
        .thenReturn(Optional.of(MatchDomain.builder().id(MATCH).startTime(START).build()));
    when(abilityRepo.findAllByMatchId(MATCH)).thenReturn(List.of());

    analyzer.analyze(MATCH);

    verify(store, never())
        .replace(eq(MATCH), org.mockito.ArgumentMatchers.argThat(l -> !l.isEmpty()));
    verify(calculator, never())
        .forPlayer(anyLong(), anyLong(), any(), anyIterable(), any(), anyInt());
  }

  @Test
  void anUnknownMatchStoresNothing() {
    when(matchRepo.findById(MATCH)).thenReturn(Optional.empty());

    analyzer.analyze(MATCH);

    verify(store, never())
        .replace(eq(MATCH), org.mockito.ArgumentMatchers.argThat(l -> !l.isEmpty()));
  }

  @Test
  void rowsWithoutSlotOrIdAndTalentsAreLeftOut() {
    when(matchRepo.findById(MATCH))
        .thenReturn(Optional.of(MatchDomain.builder().id(MATCH).startTime(START).build()));
    AbilityDomain talent = ability(0, 9);
    talent.setName("special_bonus_unique_sniper_5");
    when(abilityRepo.findAllByMatchId(MATCH))
        .thenReturn(List.of(ability(0, 1), talent, ability(0, 2), ability(5, 3), nullId()));
    PlayerMatchStatisticDomain noSlot = player(0, 10);
    noSlot.setPlayerSlot(null);
    when(playerRepo.findAllByMatchId(MATCH)).thenReturn(List.of(player(0, 10), noSlot));
    when(calculator.forPlayer(anyLong(), anyLong(), any(), anyIterable(), any(), anyInt()))
        .thenReturn(List.of());

    analyzer.analyze(MATCH);

    verify(calculator).forPlayer(eq(MATCH), eq(0L), eq(10L), eq(List.of(1L, 2L)), any(), eq(1));
    verify(calculator, never()).forPlayer(eq(MATCH), eq(5L), any(), anyIterable(), any(), anyInt());
  }

  private static AbilityDomain nullId() {
    return AbilityDomain.builder().matchId(MATCH).playerSlot(0L).build();
  }
}
