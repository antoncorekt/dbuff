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
            "The enemy took Chain Frost at pick 31; it did 38% more damage than your Dragon Blood"
                + " and was in 3 of their 4 won fights. It was still free when you took Dragon"
                + " Blood at pick 12.");
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
  void damageIsShortenedFromAThousand() {
    assertThat(MissedPickWording.damage(840)).isEqualTo("840");
    assertThat(MissedPickWording.damage(12400)).isEqualTo("12.4k");
    assertThat(MissedPickWording.damage(999960)).isEqualTo("999.9k");
  }
}
