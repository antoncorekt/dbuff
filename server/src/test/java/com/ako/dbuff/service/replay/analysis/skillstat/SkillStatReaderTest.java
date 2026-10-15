package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.resources.model.SkillStatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillStatReaderTest {
  private final SkillPickStatRepo repo = mock(SkillPickStatRepo.class);
  private final SkillStatReader reader =
      new SkillStatReader(
          repo,
          new SkillStatWording(
              new SkillStatNames() {
                @Override
                public String hero(long heroId) {
                  return "hero" + heroId;
                }

                @Override
                public String skill(long abilityId) {
                  return "skill" + abilityId;
                }
              }));

  private static SkillPickStatDomain row(
      long slot, SkillStatKind kind, long first, Long second, long games, long wins) {
    return SkillPickStatDomain.builder()
        .matchId(5L)
        .playerSlot(slot)
        .kind(kind)
        .firstId(first)
        .secondId(second)
        .games(games)
        .wins(wins)
        .windowMonths(12)
        .build();
  }

  @Test
  void aMatchIsGroupedBySlotAndEachKindIsRankedByTheWilsonBoundNotTheRawRate() {
    when(repo.findByMatchId(5L))
        .thenReturn(
            List.of(
                row(128, SkillStatKind.HERO, 20, null, 10, 5),
                row(0, SkillStatKind.SKILL, 1, null, 3, 3),
                row(0, SkillStatKind.SKILL, 2, null, 100, 60),
                row(0, SkillStatKind.HERO, 10, null, 0, 0)));

    SkillStatResponse response = reader.match(5L);

    assertThat(response.players()).extracting(p -> p.playerSlot()).containsExactly(0L, 128L);
    SkillStatResponse.PlayerStats first = response.players().getFirst();
    assertThat(first.heroId()).isEqualTo(10L);
    assertThat(first.stats())
        .extracting(s -> s.kind() + ":" + s.firstId())
        .containsExactly("HERO:10", "SKILL:2", "SKILL:1");
    assertThat(first.stats().get(1).winRate()).isEqualTo(0.6);
    assertThat(first.stats().get(0).winRate()).isNull();
    assertThat(first.stats().get(1).text()).isEqualTo("skill2: 60% in 100 games");
  }

  @Test
  void aPairCarriesItsLift() {
    SkillPickStatDomain pair = row(0, SkillStatKind.SKILL_PAIR, 1, 2L, 10, 8);
    pair.setFirstGamesWithout(10L);
    pair.setFirstWinsWithout(5L);
    pair.setSecondGamesWithout(10L);
    pair.setSecondWinsWithout(5L);
    when(repo.findByMatchId(5L)).thenReturn(List.of(pair));

    assertThat(reader.match(5L).players().getFirst().stats().getFirst().liftPoints())
        .isEqualTo(30.0, org.assertj.core.api.Assertions.within(1e-9));
  }

  @Test
  void nothingStoredGivesAnEmptyAnswer() {
    when(repo.findByMatchId(9L)).thenReturn(List.of());
    when(repo.findLatestForHero(9L)).thenReturn(List.of());

    assertThat(reader.match(9L).players()).isEmpty();
    assertThat(reader.hero(9L)).isEmpty();
  }

  @Test
  void aHeroIsAnsweredFromTheNewestRows() {
    when(repo.findLatestForHero(10L))
        .thenReturn(
            List.of(
                row(0, SkillStatKind.HERO_SKILL, 10, 2L, 4, 2),
                row(0, SkillStatKind.HERO, 10, null, 40, 20)));

    assertThat(reader.hero(10L))
        .extracting(s -> s.kind().name())
        .containsExactly("HERO", "HERO_SKILL");
  }
}
