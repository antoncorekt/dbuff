package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillStatCalculatorTest {
  private static final SkillWindow WINDOW = new SkillWindow(0, 100, 9);

  private final SkillCountRepository counts = mock(SkillCountRepository.class);
  private final SkillStatProperties properties = new SkillStatProperties();
  private final SkillStatCalculator calculator = new SkillStatCalculator(counts, properties);

  private static SkillPickStatDomain find(
      List<SkillPickStatDomain> rows, SkillStatKind kind, long first, Long second) {
    return rows.stream()
        .filter(r -> r.getKind() == kind && r.getFirstId() == first)
        .filter(r -> second == null ? r.getSecondId() == null : second.equals(r.getSecondId()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPlayerWithFourSkillsGetsFifteenRowsAndAPairKeepsTheGamesWithoutTheOther() {
    when(counts.hero(7L, WINDOW)).thenReturn(new SkillCounts(50, 25));
    when(counts.skills(any(), any()))
        .thenReturn(
            Map.of(
                1L, new SkillCounts(30, 15),
                2L, new SkillCounts(20, 8)));
    when(counts.heroWithSkills(anyLong(), any(), any()))
        .thenReturn(Map.of(1L, new SkillCounts(4, 3)));
    when(counts.pairs(any(), any()))
        .thenReturn(Map.of(new SkillPair(1, 2), new SkillCounts(10, 7)));

    List<SkillPickStatDomain> rows =
        calculator.forPlayer(5L, 128L, 7L, List.of(4L, 3L, 2L, 1L), WINDOW, 1);

    assertThat(rows).hasSize(15);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.getMatchId()).isEqualTo(5L);
              assertThat(r.getPlayerSlot()).isEqualTo(128L);
              assertThat(r.getWindowMonths()).isEqualTo(12);
              assertThat(r.getAnalysisVersion()).isEqualTo(1);
            });
    assertThat(find(rows, SkillStatKind.HERO, 7, null).getGames()).isEqualTo(50);
    assertThat(find(rows, SkillStatKind.SKILL, 3, null).getGames()).isZero();
    assertThat(find(rows, SkillStatKind.HERO_SKILL, 7, 1L).getWins()).isEqualTo(3);
    SkillPickStatDomain pair = find(rows, SkillStatKind.SKILL_PAIR, 1, 2L);
    assertThat(pair.getGames()).isEqualTo(10);
    assertThat(pair.getFirstGamesWithout()).isEqualTo(20);
    assertThat(pair.getFirstWinsWithout()).isEqualTo(8);
    assertThat(pair.getSecondGamesWithout()).isEqualTo(10);
    assertThat(pair.getSecondWinsWithout()).isEqualTo(1);
    assertThat(find(rows, SkillStatKind.SKILL_PAIR, 3, 4L).getGames()).isZero();
  }

  @Test
  void aPlayerWithNoHeroGetsNoHeroRows() {
    when(counts.skills(any(), any())).thenReturn(Map.of());
    when(counts.pairs(any(), any())).thenReturn(Map.of());

    List<SkillPickStatDomain> rows = calculator.forPlayer(5L, 0L, null, List.of(1L, 2L), WINDOW, 1);

    assertThat(rows)
        .extracting(SkillPickStatDomain::getKind)
        .containsExactly(SkillStatKind.SKILL, SkillStatKind.SKILL, SkillStatKind.SKILL_PAIR);
  }
}
