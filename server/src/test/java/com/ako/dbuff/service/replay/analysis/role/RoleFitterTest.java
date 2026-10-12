package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.role.RoleFitter.RoleFit;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleFitterTest {

  private final RoleFitter fitter = new RoleFitter(new RoleProfileProperties());

  private static FunctionProfile profile(
      LanePosition lane,
      double farm,
      double jungle,
      double roaming,
      double vision,
      double initiation,
      double disable,
      double save,
      double damage,
      double durability) {
    return new FunctionProfile(
        lane, farm, jungle, roaming, vision, initiation, disable, save, damage, durability);
  }

  private static final FunctionProfile CARRY =
      profile(LanePosition.SAFE, 1, 0, 0, 0, 0, 0, 0, 0.8, 0.2);
  private static final FunctionProfile MID =
      profile(LanePosition.MID, 0.7, 0, 0.3, 0, 0, 0.2, 0, 1, 0.2);
  private static final FunctionProfile OFFLANER =
      profile(LanePosition.OFF, 0.5, 0.2, 0.2, 0, 1, 0.6, 0, 0.3, 1);
  private static final FunctionProfile ROAMER =
      profile(LanePosition.SAFE, 0.2, 0.3, 1, 0.3, 0.6, 0.9, 0.3, 0.4, 0.2);
  private static final FunctionProfile HARD_SUPPORT =
      profile(LanePosition.SAFE, 0, 0, 0.1, 1, 0, 0.5, 1, 0.1, 0.1);

  private static List<Integer> roles(List<RoleFit> fits) {
    return fits.stream().map(RoleFit::role).toList();
  }

  @Test
  void aTypicalTeamGetsItsFiveRolesInOrder() {
    List<RoleFit> fits =
        fitter.fit(List.of(CARRY, MID, OFFLANER, ROAMER, HARD_SUPPORT), RoleStage.INTENT);

    assertThat(roles(fits)).containsExactly(1, 2, 3, 4, 5);
    assertThat(fits).allSatisfy(f -> assertThat(f.confidence()).isBetween(0.0, 1.0));
  }

  @Test
  void theOrderOfThePlayersDoesNotChangeWhoGetsWhichRole() {
    List<RoleFit> fits =
        fitter.fit(List.of(HARD_SUPPORT, OFFLANER, CARRY, ROAMER, MID), RoleStage.INTENT);

    assertThat(roles(fits)).containsExactly(5, 3, 1, 4, 2);
  }

  @Test
  void everyRoleIsUsedOnceEvenWhenAllFivePlayersLookLikeCarries() {
    List<RoleFit> fits = fitter.fit(List.of(CARRY, CARRY, CARRY, CARRY, CARRY), RoleStage.INTENT);

    assertThat(roles(fits)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
  }

  @Test
  void identicalPlayersTieAndTheFirstInOrderTakesTheFirstRole() {
    List<RoleFit> fits = fitter.fit(List.of(MID, MID, MID, MID, MID), RoleStage.INTENT);

    assertThat(roles(fits)).containsExactly(1, 2, 3, 4, 5);
    assertThat(fits).extracting(RoleFit::confidence).containsOnly(0.0);
    assertThat(fitter.fit(List.of(MID, MID, MID, MID, MID), RoleStage.INTENT))
        .usingRecursiveComparison()
        .isEqualTo(fits);
  }

  @Test
  void unknownFunctionsCountAsNeutralAndLowerTheConfidence() {
    FunctionProfile partial =
        new FunctionProfile(null, 1.0, null, null, null, null, null, null, null, null);
    List<FunctionProfile> known = List.of(CARRY, MID, OFFLANER, ROAMER, HARD_SUPPORT);

    List<RoleFit> withUnknown =
        fitter.fit(List.of(partial, MID, OFFLANER, ROAMER, HARD_SUPPORT), RoleStage.INTENT);

    assertThat(roles(withUnknown)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(withUnknown.getFirst().confidence())
        .isLessThanOrEqualTo(1.0 / RoleFunction.values().length);
    assertThat(fitter.fit(known, RoleStage.INTENT).getFirst().confidence())
        .isGreaterThan(withUnknown.getFirst().confidence());
  }

  @Test
  void aTeamOfNothingKnownStillGetsFiveRolesWithZeroConfidence() {
    List<RoleFit> fits =
        fitter.fit(java.util.Collections.nCopies(5, FunctionProfile.UNKNOWN), RoleStage.INTENT);

    assertThat(roles(fits)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(fits).extracting(RoleFit::confidence).containsOnly(0.0);
  }

  @Test
  void theLaneAddsToTheScoreOfTheRolesItSuits() {
    double[] mid =
        fitter.scores(
            new FunctionProfile(
                LanePosition.MID, null, null, null, null, null, null, null, null, null),
            RoleStage.INTENT);
    double[] none = fitter.scores(FunctionProfile.UNKNOWN, RoleStage.INTENT);

    assertThat(mid[1] - none[1]).isGreaterThan(mid[0] - none[0]);
    assertThat(fitter.scores(CARRY, RoleStage.INTENT)).hasSize(RoleFitter.ROLES);
  }

  @Test
  void aTeamOfAnotherSizeIsNotFitted() {
    assertThat(fitter.fit(List.of(CARRY, MID), RoleStage.INTENT)).isEmpty();
  }

  @Test
  void theFinalStageCountsAnUnknownFunctionAsNothingAndTheLaneLess() {
    double[] unknownIntent = fitter.scores(FunctionProfile.UNKNOWN, RoleStage.INTENT);
    double[] unknownFinal = fitter.scores(FunctionProfile.UNKNOWN, RoleStage.FINAL);
    double[] laneIntent =
        fitter.scores(profile(LanePosition.MID, 0, 0, 0, 0, 0, 0, 0, 0, 0), RoleStage.INTENT);
    double[] laneFinal =
        fitter.scores(profile(LanePosition.MID, 0, 0, 0, 0, 0, 0, 0, 0, 0), RoleStage.FINAL);

    assertThat(unknownIntent).isNotEqualTo(unknownFinal);
    assertThat(unknownFinal).containsOnly(0.0);
    assertThat(laneFinal[1]).isLessThan(laneIntent[1]).isPositive();
  }

  @Test
  void theFinalLaneBonusIsAFractionOfTheIntentOne() {
    double[] intent =
        fitter.scores(profile(LanePosition.MID, 0, 0, 0, 0, 0, 0, 0, 0, 0), RoleStage.INTENT);
    double[] fin =
        fitter.scores(profile(LanePosition.MID, 0, 0, 0, 0, 0, 0, 0, 0, 0), RoleStage.FINAL);
    double factor = new RoleProfileProperties().getFit().getFinalLaneFactor();

    assertThat(factor).isLessThan(0.5);
    assertThat(fin[1]).isEqualTo(intent[1] * factor, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void aTeamsRichestPlayerWithTheDataOfACoreTakesRoleOneFromALaneLabelledCarry() {
    FunctionProfile richOfflaner = profile(LanePosition.OFF, 1, 0, 0, 0, 0, 0, 0, 0.9, 0.6);
    FunctionProfile safeLaner = profile(LanePosition.SAFE, 0.5, 0, 0, 0, 0, 0, 0, 0.5, 0.2);
    double[] rich = fitter.scores(richOfflaner, RoleStage.FINAL);
    double[] safe = fitter.scores(safeLaner, RoleStage.FINAL);

    assertThat(RoleFitter.bestRole(rich)).isEqualTo(1);
    assertThat(rich[0] - safe[0]).isPositive();
  }
}
