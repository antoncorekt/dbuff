package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LaneVerdictTest {
  private final LaneVerdict verdict = new LaneVerdict(new LaneProperties());

  private static SideTotals side(
      TeamSide team, double score, double xp, double level, int kills, int denies) {
    return new SideTotals(
        team, 2, 20, 0, denies, score, xp, 1000.0, level, kills, 0, 0, 0, 0, false, false);
  }

  private static SideTotals withTowers(SideTotals s, boolean own, boolean enemy) {
    return new SideTotals(
        s.team(),
        s.heroes(),
        s.lastHits(),
        s.siegeLastHits(),
        s.denies(),
        s.creepScore(),
        s.xp(),
        s.gold(),
        s.averageLevel(),
        s.kills(),
        s.deaths(),
        s.damageDealt(),
        s.damageTaken(),
        s.towerDamage(),
        own,
        enemy);
  }

  @Test
  void decide_givesTheLaneToTheSideAheadOnCreepsExperienceLevelAndKills() {
    SideTotals radiant = side(TeamSide.RADIANT, 3000, 4000, 7, 2, 8);
    SideTotals dire = side(TeamSide.DIRE, 2000, 3000, 6, 0, 3);

    LaneVerdict.Verdict own = verdict.decide(radiant, dire);
    LaneVerdict.Verdict other = verdict.decide(dire, radiant);

    assertThat(own.result()).isEqualTo(LaneResult.WON);
    assertThat(other.result()).isEqualTo(LaneResult.LOST);
    assertThat(own.margin()).isGreaterThan(0.25).isCloseTo(-other.margin(), within(1e-9));
  }

  @Test
  void decide_callsASmallMarginEven() {
    SideTotals radiant = side(TeamSide.RADIANT, 2050, 3000, 6, 0, 3);
    SideTotals dire = side(TeamSide.DIRE, 2000, 3000, 6, 0, 3);

    assertThat(verdict.decide(radiant, dire).result()).isEqualTo(LaneResult.EVEN);
    assertThat(verdict.decide(dire, radiant).result()).isEqualTo(LaneResult.EVEN);
  }

  @Test
  void decide_movesTheMarginByAFallenLaneTower() {
    SideTotals radiant = side(TeamSide.RADIANT, 2000, 3000, 6, 0, 3);
    SideTotals dire = side(TeamSide.DIRE, 2000, 3000, 6, 0, 3);

    LaneVerdict.Verdict lost = verdict.decide(withTowers(radiant, true, false), dire);
    LaneVerdict.Verdict won = verdict.decide(withTowers(radiant, false, true), dire);

    assertThat(lost.result()).isEqualTo(LaneResult.LOST);
    assertThat(lost.margin()).isCloseTo(-0.25, within(1e-9));
    assertThat(won.result()).isEqualTo(LaneResult.WON);
  }

  @Test
  void decide_isUncontestedWhenTheOtherSideHasNoHero() {
    SideTotals radiant = side(TeamSide.RADIANT, 2000, 3000, 6, 0, 3);
    SideTotals nobody = SideTotals.of(TeamSide.DIRE, List.of(), false, false);

    assertThat(verdict.decide(radiant, nobody).result()).isEqualTo(LaneResult.UNCONTESTED);
  }

  @Test
  void decide_treatsMissingExperienceAsNoDifference() {
    SideTotals radiant =
        new SideTotals(
            TeamSide.RADIANT, 1, 20, 0, 3, 2400, null, null, 6, 0, 0, 0, 0, 0, false, false);
    SideTotals dire = side(TeamSide.DIRE, 2000, 3000, 6, 0, 3);

    assertThat(verdict.decide(radiant, dire).margin()).isFinite();
  }

  @Test
  void decide_doesNotFavourTheSideWithMoreHeroes() {
    SideTotals two =
        new SideTotals(
            TeamSide.RADIANT, 2, 40, 0, 6, 4000, 6000.0, 2000.0, 6, 2, 0, 0, 0, 0, false, false);
    SideTotals one =
        new SideTotals(
            TeamSide.DIRE, 1, 20, 0, 3, 2000, 3000.0, 1000.0, 6, 1, 0, 0, 0, 0, false, false);

    assertThat(verdict.decide(two, one).result()).isEqualTo(LaneResult.EVEN);
    assertThat(verdict.decide(one, two).result()).isEqualTo(LaneResult.EVEN);
  }

  @Test
  void decide_givesTheLaneToTheSideWithTheBetterAveragePerHero() {
    SideTotals two =
        new SideTotals(
            TeamSide.RADIANT, 2, 20, 0, 3, 2000, 3000.0, 2000.0, 6, 0, 0, 0, 0, 0, false, false);
    SideTotals one =
        new SideTotals(
            TeamSide.DIRE, 1, 20, 0, 3, 2000, 3000.0, 1000.0, 6, 1, 0, 0, 0, 0, false, false);

    assertThat(verdict.decide(one, two).result()).isEqualTo(LaneResult.WON);
  }
}
