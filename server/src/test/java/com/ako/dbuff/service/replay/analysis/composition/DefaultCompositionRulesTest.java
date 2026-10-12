package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCompositionRulesTest {

  private final CompositionProperties properties = new CompositionProperties();
  private final DefaultCompositionRules rules = new DefaultCompositionRules(properties);

  private static FunctionProfile profile(
      Double farm,
      Double vision,
      Double initiation,
      Double disable,
      Double save,
      Double damage,
      Double durability) {
    return new FunctionProfile(
        null, farm, null, null, vision, initiation, disable, save, damage, durability);
  }

  /** Everything the team needs, spread so that no token is raised. */
  private static List<FunctionProfile> balanced() {
    return new ArrayList<>(
        List.of(
            profile(0.9, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0),
            profile(0.5, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0),
            profile(0.3, 0.0, 0.8, 0.5, 0.0, 0.0, 0.8),
            profile(0.2, 0.0, 0.0, 0.8, 0.0, 0.0, 0.0),
            profile(0.0, 0.7, 0.0, 0.0, 0.8, 0.0, 0.0)));
  }

  private static List<TeamMember> team(List<FunctionProfile> profiles) {
    List<TeamMember> members = new ArrayList<>();
    for (int i = 0; i < profiles.size(); i++) {
      members.add(new TeamMember(i, i + 1, null, profiles.get(i)));
    }
    return members;
  }

  private CompositionVerdict finalVerdict(List<FunctionProfile> profiles) {
    return rules.evaluate(CompositionStage.FINAL, team(profiles));
  }

  @Test
  void aBalancedTeamHasNoFindingAndSaysSo() {
    CompositionVerdict verdict = finalVerdict(balanced());

    assertThat(verdict.tokens()).isEmpty();
    assertThat(verdict.unknown()).isEmpty();
    assertThat(verdict.summary()).isEqualTo("no gaps found");
  }

  @Test
  void supportsAreCountedFromFarmPriority() {
    List<FunctionProfile> team = balanced();
    team.set(0, profile(0.1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    team.set(1, profile(0.2, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    team.set(2, profile(0.7, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));

    assertThat(finalVerdict(team).tokens()).contains("SUPPORTS_4").doesNotContain("CARRIES_0");
  }

  @Test
  void aTeamWithoutAnySupportOrCarryHasThemCountedAsZero() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(profile(0.4, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    assertThat(finalVerdict(team).tokens()).contains("CARRIES_0", "SUPPORTS_0");
  }

  @Test
  void threeCarriesAreAFinding() {
    List<FunctionProfile> team = balanced();
    team.set(1, profile(0.8, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0));
    team.set(2, profile(0.7, 0.0, 0.8, 0.5, 0.0, 0.0, 0.8));

    assertThat(finalVerdict(team).tokens()).contains("CARRIES_3");
    assertThat(finalVerdict(team).summary()).contains("3 carries");
  }

  @Test
  void aTeamAtEveryFarmThresholdIsTopHeavyAndNothingElseIsMissingFarmWise() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(profile(0.6, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    assertThat(finalVerdict(team).tokens())
        .contains("CARRIES_5", "TOP_HEAVY")
        .doesNotContain("NO_FARM_ENGINE");
  }

  @Test
  void aTeamWhereNobodyFarmsHasNoFarmEngine() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(profile(0.2, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    assertThat(finalVerdict(team).tokens()).contains("NO_FARM_ENGINE", "SUPPORTS_5");
  }

  @Test
  void eachMissingFunctionRaisesItsOwnToken() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(profile(i == 0 ? 0.9 : 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    assertThat(finalVerdict(team).tokens())
        .contains(
            "NO_INITIATOR",
            "NO_DISABLE",
            "NO_WAVECLEAR",
            "NO_FRONTLINE",
            "NO_SAVE_HEAL",
            "NO_VISION_DUTY");
  }

  @Test
  void theVisionDutyIsNotJudgedFromTheDraft() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(profile(i == 0 ? 0.9 : 0.2, null, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    CompositionVerdict pick = rules.evaluate(CompositionStage.PICK, team(team));

    assertThat(pick.tokens()).contains("NO_INITIATOR").doesNotContain("NO_VISION_DUTY");
    assertThat(pick.unknown()).doesNotContain(RoleFunction.VISION_DUTY);
  }

  @Test
  void aFunctionOnlyOnePlayerReachesIsEnough() {
    List<FunctionProfile> team = balanced();
    team.set(3, profile(0.2, 0.0, 0.0, 0.4, 0.0, 0.0, 0.0));
    team.set(2, profile(0.3, 0.0, 0.8, 0.0, 0.0, 0.0, 0.8));

    assertThat(finalVerdict(team).tokens()).doesNotContain("NO_DISABLE");
  }

  @Test
  void aFunctionNoKnownPlayerHasStaysUnknownWhenSomeoneMightHaveIt() {
    List<FunctionProfile> team = balanced();
    team.set(3, profile(0.2, 0.0, 0.0, null, 0.0, 0.0, 0.0));
    team.set(2, profile(0.3, 0.0, 0.8, null, 0.0, 0.0, 0.8));
    team.set(4, profile(0.0, 0.7, 0.0, null, 0.8, 0.0, 0.0));

    CompositionVerdict verdict = finalVerdict(team);

    assertThat(verdict.tokens()).doesNotContain("NO_DISABLE");
    assertThat(verdict.unknown()).containsExactly(RoleFunction.DISABLE);
    assertThat(verdict.summary()).isEqualTo("nothing found. Unknown: disable");
  }

  @Test
  void aFunctionKnownForSomeOnlyIsMissingNowhereIfOneKnownPlayerHasIt() {
    List<FunctionProfile> team = balanced();
    team.set(3, profile(0.2, 0.0, 0.0, null, 0.0, 0.0, 0.0));

    CompositionVerdict verdict = finalVerdict(team);

    assertThat(verdict.unknown()).isEmpty();
  }

  @Test
  void unknownFarmGivesNoCountsAndNoBalanceToken() {
    List<FunctionProfile> team = balanced();
    team.set(0, profile(null, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0));
    team.set(1, profile(0.1, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0));
    team.set(2, profile(0.1, 0.0, 0.8, 0.5, 0.0, 0.0, 0.8));
    team.set(3, profile(0.1, 0.0, 0.0, 0.8, 0.0, 0.0, 0.0));
    team.set(4, profile(0.1, 0.7, 0.0, 0.0, 0.8, 0.0, 0.0));

    CompositionVerdict verdict = finalVerdict(team);

    assertThat(verdict.tokens()).isEmpty();
    assertThat(verdict.unknown()).containsExactly(RoleFunction.FARM_PRIORITY);
  }

  @Test
  void aProfileOfUnknownsRaisesNoTokenAtAllAndNamesEveryFunction() {
    List<FunctionProfile> team = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      team.add(FunctionProfile.UNKNOWN);
    }

    CompositionVerdict verdict = finalVerdict(team);

    assertThat(verdict.tokens()).isEmpty();
    assertThat(verdict.unknown())
        .containsExactlyInAnyOrder(
            RoleFunction.FARM_PRIORITY,
            RoleFunction.INITIATION,
            RoleFunction.DISABLE,
            RoleFunction.DAMAGE,
            RoleFunction.DURABILITY,
            RoleFunction.SAVE_HEAL,
            RoleFunction.VISION_DUTY);
    assertThat(verdict.summary()).startsWith("nothing found. Unknown: farm priority");
  }

  @Test
  void intentNotPlayedNeedsAnIntentAndADifferentRole() {
    List<TeamMember> same = new ArrayList<>();
    List<TeamMember> changed = new ArrayList<>();
    List<TeamMember> noIntent = new ArrayList<>();
    List<FunctionProfile> profiles = balanced();
    for (int i = 0; i < 5; i++) {
      same.add(new TeamMember(i, i + 1, i + 1, profiles.get(i)));
      changed.add(new TeamMember(i, i + 1, i == 0 ? 5 : i + 1, profiles.get(i)));
      noIntent.add(new TeamMember(i, i + 1, null, profiles.get(i)));
    }

    assertThat(rules.evaluate(CompositionStage.FINAL, same).tokens()).isEmpty();
    assertThat(rules.evaluate(CompositionStage.FINAL, noIntent).tokens()).isEmpty();
    assertThat(rules.evaluate(CompositionStage.FINAL, changed).tokens())
        .containsExactly("INTENT_NOT_PLAYED");
    assertThat(rules.evaluate(CompositionStage.PICK, changed).tokens()).isEmpty();
    properties.setIntentNotPlayedPlayers(2);
    assertThat(rules.evaluate(CompositionStage.FINAL, changed).tokens()).isEmpty();
  }

  @Test
  void thresholdsComeFromTheProperties() {
    List<FunctionProfile> team = balanced();
    assertThat(finalVerdict(team).tokens()).doesNotContain("NO_DISABLE");

    properties.getMinimum().put(RoleFunction.DISABLE, 0.9);
    properties.setCarryFarm(0.95);
    properties.setCarriesNotedFrom(1);
    properties.setSupportsNotedFrom(1);

    assertThat(finalVerdict(team).tokens())
        .contains("NO_DISABLE", "CARRIES_0")
        .contains("SUPPORTS_2");
  }

  @Test
  void supportsAllPickAndAbilityDraftOnly() {
    assertThat(rules.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(rules.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(
            rules.supports(
                new GameModeProfile() {
                  public long gameModeId() {
                    return 1;
                  }

                  public String name() {
                    return "Other";
                  }
                }))
        .isFalse();
  }

  @Test
  void aMinimumMissingForAFunctionFailsAtStartup() {
    properties.getMinimum().remove(RoleFunction.SAVE_HEAL);

    assertThatThrownBy(properties::validate).hasMessageContaining("SAVE_HEAL");
  }
}
