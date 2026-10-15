package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SkillLookupTest {
  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  private SkillCountRepository counts;
  private SkillLookup lookup;

  @BeforeEach
  void setUp() {
    counts = Mockito.mock(SkillCountRepository.class);
    lookup = new SkillLookup(counts, new SkillStatProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void theWindowIsTheConfiguredMonthsBeforeNowWithNoMatchLeftOut() {
    Mockito.when(counts.hero(Mockito.anyLong(), Mockito.any())).thenReturn(SkillCounts.NONE);
    Mockito.when(counts.heroWithSkills(Mockito.anyLong(), Mockito.any(), Mockito.any()))
        .thenReturn(Map.of());

    lookup.hero(7, List.of(1L));

    ArgumentCaptor<SkillWindow> window = ArgumentCaptor.forClass(SkillWindow.class);
    Mockito.verify(counts).hero(Mockito.eq(7L), window.capture());
    assertThat(window.getValue().fromEpoch())
        .isEqualTo(Instant.parse("2025-10-15T12:00:00Z").getEpochSecond());
    assertThat(window.getValue().toEpoch()).isGreaterThan(NOW.getEpochSecond());
    assertThat(window.getValue().excludedMatchId()).isNegative();
  }

  @Test
  void aHeroListsItsSkillsMostReliableFirstAndDropsSkillsWithNoGames() {
    Mockito.when(counts.hero(Mockito.anyLong(), Mockito.any())).thenReturn(new SkillCounts(50, 30));
    Mockito.when(counts.heroWithSkills(Mockito.anyLong(), Mockito.any(), Mockito.any()))
        .thenReturn(Map.of(1L, new SkillCounts(2, 2), 2L, new SkillCounts(100, 60)));

    SkillAnswer answer = lookup.hero(7, List.of(1L, 2L, 3L));

    assertThat(answer.subject().getKind()).isEqualTo(SkillStatKind.HERO);
    assertThat(answer.subject().getGames()).isEqualTo(50);
    assertThat(answer.details())
        .extracting(SkillPickStatDomain::getSecondId, SkillPickStatDomain::getGames)
        .containsExactly(tuple(2L, 100L), tuple(1L, 2L));
  }

  @Test
  void aHeroWithNoGamesRunsNoSkillQuery() {
    Mockito.when(counts.hero(Mockito.anyLong(), Mockito.any())).thenReturn(SkillCounts.NONE);

    SkillAnswer answer = lookup.hero(7, List.of(1L, 2L));

    assertThat(answer.subject().getGames()).isZero();
    assertThat(answer.details()).isEmpty();
    Mockito.verify(counts, Mockito.never())
        .heroWithSkills(Mockito.anyLong(), Mockito.any(), Mockito.any());
  }

  @Test
  void aSkillNamesTheHeroItDidBestOnByTheWilsonBoundNotTheRawRate() {
    Mockito.when(counts.skills(Mockito.eq(Set.of(5L)), Mockito.any()))
        .thenReturn(Map.of(5L, new SkillCounts(106, 63)));
    Mockito.when(counts.heroesWithSkill(Mockito.eq(5L), Mockito.any()))
        .thenReturn(
            Map.of(
                10L, new SkillCounts(2, 2), 11L, new SkillCounts(100, 60), 12L, SkillCounts.NONE));

    SkillAnswer answer = lookup.skill(5);

    assertThat(answer.details()).hasSize(1);
    assertThat(answer.details().get(0).getFirstId()).isEqualTo(11L);
    assertThat(answer.details().get(0).getSecondId()).isEqualTo(5L);
  }

  @Test
  void aSkillNobodyHeldHasNoBestHero() {
    Mockito.when(counts.skills(Mockito.any(), Mockito.any())).thenReturn(Map.of());
    Mockito.when(counts.heroesWithSkill(Mockito.anyLong(), Mockito.any())).thenReturn(Map.of());

    SkillAnswer answer = lookup.skill(5);

    assertThat(answer.subject().getGames()).isZero();
    assertThat(answer.details()).isEmpty();
  }

  @Test
  void aPairKeepsTheGamesEachSkillHadWithoutTheOther() {
    Mockito.when(counts.skills(Mockito.any(), Mockito.any()))
        .thenReturn(Map.of(1L, new SkillCounts(114, 58), 2L, new SkillCounts(114, 58)));
    Mockito.when(counts.pairs(Mockito.any(), Mockito.any()))
        .thenReturn(Map.of(SkillPair.of(1, 2), new SkillCounts(14, 8)));

    SkillPickStatDomain pair = lookup.pair(2, 1).subject();

    assertThat(pair.getFirstId()).isEqualTo(1L);
    assertThat(pair.getSecondId()).isEqualTo(2L);
    assertThat(pair.getGames()).isEqualTo(14);
    assertThat(pair.getFirstGamesWithout()).isEqualTo(100);
    assertThat(pair.getSecondWinsWithout()).isEqualTo(50);
  }
}
