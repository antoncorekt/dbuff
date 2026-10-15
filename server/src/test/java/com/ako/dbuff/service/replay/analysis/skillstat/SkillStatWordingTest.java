package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillStatWordingTest {
  private final SkillStatWording wording =
      new SkillStatWording(
          new SkillStatNames() {
            @Override
            public String hero(long heroId) {
              return "Pudge";
            }

            @Override
            public String skill(long abilityId) {
              return abilityId == 1 ? "Storm Bolt" : "Thunder Clap";
            }
          });

  private static SkillPickStatDomain row(
      SkillStatKind kind, long games, long wins, long... withoutFirstSecond) {
    SkillPickStatDomain.SkillPickStatDomainBuilder b =
        SkillPickStatDomain.builder()
            .kind(kind)
            .firstId(1L)
            .secondId(kind == SkillStatKind.HERO || kind == SkillStatKind.SKILL ? null : 2L)
            .games(games)
            .wins(wins)
            .windowMonths(12);
    if (withoutFirstSecond.length == 4) {
      b.firstGamesWithout(withoutFirstSecond[0])
          .firstWinsWithout(withoutFirstSecond[1])
          .secondGamesWithout(withoutFirstSecond[2])
          .secondWinsWithout(withoutFirstSecond[3]);
    }
    return b.build();
  }

  @Test
  void aPairIsWordedWithItsRateGamesAndLift() {
    String text = wording.describe(row(SkillStatKind.SKILL_PAIR, 14, 8, 100, 52, 100, 52));

    assertThat(text)
        .isEqualTo(
            "Storm Bolt with Thunder Clap: 57% in 14 games, 5 points above what each gives alone");
  }

  @Test
  void aSmallCountSaysOnly() {
    assertThat(wording.describe(row(SkillStatKind.SKILL, 3, 2)))
        .isEqualTo("Storm Bolt: 67% in only 3 games");
    assertThat(wording.describe(row(SkillStatKind.SKILL, 1, 1)))
        .isEqualTo("Storm Bolt: 100% in only 1 game");
  }

  @Test
  void aLowerPairSaysBelowAndAnEqualOneSaysTheSame() {
    assertThat(wording.describe(row(SkillStatKind.SKILL_PAIR, 20, 8, 100, 50, 100, 50)))
        .endsWith("10 points below what each gives alone");
    assertThat(wording.describe(row(SkillStatKind.SKILL_PAIR, 20, 10, 100, 50, 100, 50)))
        .endsWith("50% in 20 games, the same as what each gives alone");
  }

  @Test
  void aPairWithoutBaselineHasNoLiftWords() {
    assertThat(wording.describe(row(SkillStatKind.SKILL_PAIR, 20, 10, 0, 0, 0, 0)))
        .isEqualTo("Storm Bolt with Thunder Clap: 50% in 20 games");
  }

  @Test
  void noGamesIsSaidPlainly() {
    assertThat(wording.describe(row(SkillStatKind.HERO, 0, 0)))
        .isEqualTo("Pudge: no games in the last 12 months");
  }

  @Test
  void aHeroWithASkillNamesBoth() {
    assertThat(wording.describe(row(SkillStatKind.HERO_SKILL, 30, 15)))
        .isEqualTo("Pudge with Thunder Clap: 50% in 30 games");
  }
}
