package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class VisionReasonContributorTest {
  private final VisionReasonContributor contributor =
      new VisionReasonContributor(
          new ReasonProperties(),
          new ModeTestKits(Map.of(7L, ModeTestKits.hero(7, "Riki")), Map.of()));

  private static FightVisionDomain vision(String team, String verdict, int observers) {
    return FightVisionDomain.builder()
        .team(team)
        .verdict(verdict)
        .activeObservers(observers)
        .build();
  }

  private static LateFightContext context(String winner, FightVisionDomain... visions) {
    FightContext fight =
        new FightContext(
            1,
            TestModes.ALL_PICK,
            FightDomain.builder().fightIndex(0).startSeconds(600.0).winner(winner).build(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(7L, 7L),
            List.of());
    return new LateFightContext(fight, List.of(visions), List.of());
  }

  @Test
  void noVisionOfTheLoserAgainstAWinnerWithVisionIsATeamReasonOfTheLoser() {
    List<Reason> reasons =
        contributor.contribute(
            context("RADIANT", vision("RADIANT", "FINE", 2), vision("DIRE", "NO_VISION", 0)));

    assertThat(reasons)
        .extracting(Reason::code, Reason::scope, Reason::team, Reason::source, Reason::severity)
        .containsExactly(
            tuple("NO_VISION_BEFORE_FIGHT", ReasonScope.TEAM, TeamSide.DIRE, "VISION", 0.4));
    assertThat(reasons.get(0).evidence())
        .isEqualTo("Dire had no ward covering the fight when it began, Radiant had 2 wards");
  }

  @Test
  void noVisionOnBothSidesGivesNoReason() {
    assertThat(
            contributor.contribute(
                context(
                    "RADIANT", vision("RADIANT", "NO_VISION", 0), vision("DIRE", "NO_VISION", 0))))
        .isEmpty();
  }

  @Test
  void aWinnerWithoutVisionWhileTheLoserHadSomeGivesNoReason() {
    assertThat(
            contributor.contribute(
                context("RADIANT", vision("RADIANT", "NO_VISION", 0), vision("DIRE", "FINE", 1))))
        .isEmpty();
  }

  @Test
  void aLoserWithNoVisionRowOfTheWinnerGivesNoReason() {
    assertThat(contributor.contribute(context("RADIANT", vision("DIRE", "NO_VISION", 0))))
        .isEmpty();
  }

  @Test
  void anInvisibleHeroWithoutDetectionNamesTheHero() {
    FightVisionDomain loser = vision("DIRE", "INVISIBLE_HERO_NO_DETECTION", 1);
    loser.setInvisibleHeroes("7");

    List<Reason> reasons =
        contributor.contribute(context("RADIANT", vision("RADIANT", "FINE", 1), loser));

    assertThat(reasons)
        .extracting(Reason::code, Reason::severity)
        .containsExactly(tuple("NO_DETECTION_VS_INVISIBLE", 0.4));
    assertThat(reasons.get(0).evidence())
        .isEqualTo("Riki was invisible in the fight and Dire had no detection");
  }

  @Test
  void anInvisibleHeroWithNoStoredSlotIsStillAReason() {
    List<Reason> reasons =
        contributor.contribute(
            context("DIRE", vision("RADIANT", "INVISIBLE_HERO_NO_DETECTION", 1)));

    assertThat(reasons.get(0).evidence())
        .isEqualTo("An enemy hero was invisible in the fight and Radiant had no detection");
  }

  @Test
  void aLateWardIsAWeakTeamReason_andSeveralVerdictsGiveSeveralReasons() {
    FightVisionDomain loser = vision("DIRE", "NO_VISION,INVISIBLE_HERO_NO_DETECTION", 0);
    FightVisionDomain late = vision("RADIANT", "LATE_WARD", 1);
    late.setLateWardSeconds(12);

    assertThat(contributor.contribute(context("DIRE", loser, late)))
        .extracting(Reason::code, Reason::team, Reason::severity)
        .containsExactly(tuple("LATE_WARD", TeamSide.RADIANT, 0.15));
    assertThat(contributor.contribute(context("RADIANT", vision("RADIANT", "FINE", 1), loser)))
        .extracting(Reason::code)
        .containsExactly("NO_VISION_BEFORE_FIGHT", "NO_DETECTION_VS_INVISIBLE");
  }

  @Test
  void aLateWardEvidenceSaysHowLate() {
    FightVisionDomain loser = vision("DIRE", "LATE_WARD", 1);
    loser.setLateWardSeconds(12);

    assertThat(contributor.contribute(context("RADIANT", loser)).get(0).evidence())
        .isEqualTo("Dire's wards near the fight were placed 12 s before it began");
  }

  @Test
  void aFightWithNoWinnerOrNoVisionRowGivesNothing() {
    assertThat(contributor.contribute(context(null, vision("DIRE", "NO_VISION", 0)))).isEmpty();
    assertThat(contributor.contribute(context("RADIANT"))).isEmpty();
  }

  @Test
  void supportsEveryMode_andOwnsTheVisionSource() {
    assertThat(contributor.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(contributor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(contributor.source()).isEqualTo("VISION");
  }
}
