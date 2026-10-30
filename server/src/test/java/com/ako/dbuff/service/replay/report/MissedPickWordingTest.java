package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MissedPickDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissedPickWordingTest {
  private static final AbilityLabels LABELS =
      name -> name.equals("lich_chain_frost") ? "Chain Frost" : "Dragon Blood";

  private MissedPickDomain.MissedPickDomainBuilder row() {
    return MissedPickDomain.builder()
        .takenAbility("dragon_knight_dragon_blood")
        .takenTurn(12)
        .candidateAbility("lich_chain_frost")
        .candidateTurn(31)
        .enemyWonFights(4)
        .candidateWonFights(3)
        .takenWonFights(1);
  }

  @Test
  void aDamageGapIsAPercentageAndTheWonFightsAreCounted() {
    String line =
        MissedPickWording.line(
            row().basis("DAMAGE").takenDamage(1000L).candidateDamage(1380L).ratio(1.38).build(),
            LABELS);

    assertThat(line)
        .isEqualTo(
            "The enemy took Chain Frost at turn 31; it did 38% more damage than your Dragon Blood"
                + " and was in 3 of their 4 won fights. It was still free when you took Dragon"
                + " Blood at turn 12.");
  }

  @Test
  void thePercentIsNeverRoundedUp() {
    String line =
        MissedPickWording.line(
            row().basis("DAMAGE").takenDamage(1000L).candidateDamage(1104L).ratio(1.104).build(),
            LABELS);

    assertThat(line).contains("it did 10% more damage");
  }

  @Test
  void twoSkillsWithoutDamageAreComparedOnFights() {
    String line =
        MissedPickWording.line(
            row().basis("FIGHTS").takenDamage(0L).candidateDamage(0L).enemyWonFights(1).build(),
            LABELS);

    assertThat(line)
        .contains("neither did damage, but it was in 3 of their 1 won fight and your Dragon Blood")
        .contains("was in 1.");
  }

  @Test
  void twoSkillsOfTheSameFunctionAreComparedOnFights() {
    String line =
        MissedPickWording.line(
            row().basis("FIGHTS:SAVE_HEAL").takenDamage(0L).candidateDamage(0L).build(), LABELS);

    assertThat(line)
        .contains("both are save and heal skills; it was in 3 of their 4 won fights, yours in 1.");
  }

  @Test
  void thePatchAverageAndGameCountAreAppendedAndCarriedIsSaid() {
    MissedPickDomain.MissedPickDomainBuilder damage =
        row().basis("DAMAGE").takenDamage(1000L).candidateDamage(1380L).ratio(1.38);

    assertThat(
            MissedPickWording.line(
                damage.candidatePatchAverage(4140.0).candidatePatchGames(23).carried(false).build(),
                LABELS))
        .contains("won fights (its patch average is 4.1k in 23 games). It was");
    assertThat(MissedPickWording.line(damage.candidatePatchGames(1).carried(true).build(), LABELS))
        .contains("(its patch average is 4.1k in 1 game, probably carried by a fed hero).");
    assertThat(MissedPickWording.line(damage.candidatePatchGames(0).build(), LABELS))
        .contains("(no other games of it on this patch).");
  }

  @Test
  void aMissingPatchPrintsNothing() {
    assertThat(
            MissedPickWording.line(
                row().basis("DAMAGE").takenDamage(1L).candidateDamage(2L).ratio(2.0).build(),
                LABELS))
        .doesNotContain("patch");
  }

  @Test
  void damageIsShortenedFromAThousand() {
    assertThat(MissedPickWording.damage(840)).isEqualTo("840");
    assertThat(MissedPickWording.damage(12400)).isEqualTo("12.4k");
    assertThat(MissedPickWording.damage(999960)).isEqualTo("999.9k");
  }
}
