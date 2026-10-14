package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightReasonDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonWordingTest {
  private static final ReasonWording WORDING = new ReasonWording(Prettify::ability);

  private static FightReasonDomain player(String code, String evidence) {
    return FightReasonDomain.builder().scope("PLAYER").code(code).evidence(evidence).build();
  }

  private static FightReasonDomain team(String code) {
    return FightReasonDomain.builder()
        .scope("TEAM")
        .code(code)
        .evidence("Radiant, the losing team: x")
        .build();
  }

  private static FightReasonDomain teamWith(String code, String evidence) {
    return FightReasonDomain.builder().scope("TEAM").code(code).evidence(evidence).build();
  }

  @Test
  void phrase_startsAPlayerReasonWithTheHero() {
    assertThat(WORDING.phrase(player("NO_TP", "ABSENT_ALIVE"), "Venomancer"))
        .isEqualTo("Venomancer had no teleport");
    assertThat(WORDING.phrase(player("DIED_FIRST", "died at 23:10 to x"), "Sven"))
        .isEqualTo("Sven died first");
  }

  @Test
  void phrase_takesWhatTheFarmingEvidenceSays() {
    String evidence = "carry was farming a lane when the fight began and stayed out of it";

    assertThat(WORDING.phrase(player("CARRY_FARMING_ELSEWHERE", evidence), "Terrorblade"))
        .isEqualTo("Terrorblade was farming a lane");
    assertThat(WORDING.phrase(player("CARRY_FARMING_ELSEWHERE", "odd"), "Terrorblade"))
        .isEqualTo("Terrorblade was farming elsewhere");
  }

  @Test
  void phrase_namesTheAbilitiesFromTheEvidence() {
    assertThat(
            WORDING.phrase(player("ITEM_ON_COOLDOWN", " item_black_king_bar, item_blink "), "Axe"))
        .isEqualTo("Axe had Black King Bar and Blink on cooldown");
  }

  @Test
  void phrase_wordsATeamReasonFromItsToken() {
    assertThat(WORDING.phrase(team("NO_DISABLE"), "ignored")).isEqualTo("the team had no disable");
    assertThat(WORDING.phrase(team("CARRIES_2"), "ignored")).isEqualTo("the team had 2 carries");
    assertThat(WORDING.phrase(team("TOP_HEAVY"), "ignored"))
        .isEqualTo("the team: everyone wants the farm");
  }

  @Test
  void phrase_wordsATeamReasonThatIsNoCompositionTokenFromItsEvidence() {
    assertThat(
            WORDING.phrase(
                teamWith(
                    "WEAK_COMBINATION",
                    " Radiant drafted skills whose best player reaches only: x "),
                "ignored"))
        .isEqualTo("Radiant drafted skills whose best player reaches only: x");
    assertThat(
            WORDING.phrase(
                teamWith("STRONG_SKILL_LEFT", "strong skills nobody drafted: a (b)"), "ignored"))
        .isEqualTo("the team: strong skills nobody drafted: a (b)");
    assertThat(WORDING.phrase(teamWith("NEW_MODE_CODE", "short note"), "ignored"))
        .isEqualTo("the team: short note");
    assertThat(WORDING.phrase(teamWith("WEAK_COMBINATION", null), "ignored"))
        .isEqualTo("the team had a gap");
  }

  @Test
  void phrase_fallsBackToTheTrimmedEvidenceForAnUnknownCode() {
    assertThat(WORDING.phrase(player("NEW_CODE", "  something odd "), "Sven"))
        .isEqualTo("Sven: something odd");
    assertThat(WORDING.phrase(player("NEW_CODE", null), "Sven")).isEqualTo("Sven new code");
  }

  @Test
  void phrase_dropsTheAbilityListWhenTheEvidenceIsBlank() {
    assertThat(WORDING.phrase(player("NO_MANA", " "), "Axe")).isEqualTo("Axe ran out of mana");
    assertThat(WORDING.phrase(player("ABILITY_ON_COOLDOWN", null), "Axe"))
        .isEqualTo("Axe had an ability on cooldown");
  }

  @Test
  void phrase_survivesATeamReasonWithNoCode() {
    assertThat(WORDING.phrase(teamWith(null, null), "x")).isEqualTo("the team had a gap");
    assertThat(WORDING.phrase(teamWith("", " "), "x")).isEqualTo("the team had a gap");
  }

  @Test
  void phrase_doesNotRepeatTheHeroWhenTheEvidenceStartsWithIt() {
    assertThat(
            WORDING.phrase(
                player("INTENT_NOT_PLAYED", "Sven was drafted as offlane and played hard support"),
                "Sven"))
        .isEqualTo("Sven was drafted as offlane and played hard support");
  }

  @Test
  void phrase_wordsARuneAdvantageWithTheLeadAndAnArticle() {
    String evidence =
        "double damage rune taken at 5:20 by Windrunner, 40 s from the fight start, active in the"
            + " fight";

    assertThat(WORDING.phrase(player("RUNE_ADVANTAGE", evidence), "Windrunner"))
        .isEqualTo("Windrunner took a double damage rune 40 s before the fight");
    assertThat(
            WORDING.phrase(
                player(
                    "RUNE_ADVANTAGE",
                    evidence.replace("double damage", "arcane").replace("40 s", "-12 s")),
                "Windrunner"))
        .isEqualTo("Windrunner took an arcane rune 12 s into the fight");
    assertThat(WORDING.phrase(player("RUNE_ADVANTAGE", "odd"), "Windrunner"))
        .isEqualTo("Windrunner had a rune effect");
  }

  @Test
  void phrase_wordsTheEnemyRuneAdvantageAsATeamReason() {
    String evidence =
        "haste rune taken at 5:20 by Windrunner, 10 s from the fight start, active in the fight";

    assertThat(WORDING.phrase(teamWith("ENEMY_RUNE_ADVANTAGE", evidence), "ignored"))
        .isEqualTo("the enemy Windrunner took a haste rune 10 s before the fight");
    assertThat(WORDING.phrase(teamWith("ENEMY_RUNE_ADVANTAGE", "odd"), "ignored"))
        .isEqualTo("the team: the enemy had a rune effect");
  }

  @Test
  void phrase_listsEveryRuneOfOneReason() {
    String first =
        "haste rune taken at 5:20 by Windrunner, 10 s from the fight start, active in the fight";
    String second =
        "arcane rune taken at 5:25 by Axe, 5 s from the fight start, active in the fight";

    assertThat(
            WORDING.phrase(
                player("RUNE_ADVANTAGE", first + "; " + first.replace("haste", "shield")),
                "Windrunner"))
        .isEqualTo(
            "Windrunner took a haste rune 10 s before the fight and a shield rune 10 s before the"
                + " fight");
    assertThat(WORDING.phrase(teamWith("ENEMY_RUNE_ADVANTAGE", first + "; " + second), "x"))
        .isEqualTo(
            "the enemy Windrunner took a haste rune 10 s before the fight, Axe took an arcane rune"
                + " 5 s before the fight");
  }

  @Test
  void phrase_namesCooldownAbilitiesThroughTheLabels() {
    ReasonWording wording =
        new ReasonWording(
            name ->
                switch (name) {
                  case "terrorblade_metamorphosis" -> "Metamorphosis";
                  case "death_prophet_exorcism" -> "Exorcism";
                  default -> Prettify.ability(name);
                });

    assertThat(
            wording.phrase(
                player("ABILITY_ON_COOLDOWN", "terrorblade_metamorphosis, death_prophet_exorcism"),
                "Nature's Prophet"))
        .isEqualTo("Nature's Prophet had Metamorphosis and Exorcism on cooldown");
  }

  @Test
  void phrase_wordsTheVisionAndRotationReasons() {
    assertThat(WORDING.phrase(teamWith("NO_VISION_BEFORE_FIGHT", "Dire had no ward"), "x"))
        .isEqualTo("the team had no vision near the fight, the enemy did");
    assertThat(
            WORDING.phrase(
                teamWith(
                    "NO_DETECTION_VS_INVISIBLE",
                    "Riki was invisible in the fight and Dire had no detection"),
                "x"))
        .isEqualTo("the team had no detection against an invisible enemy hero");
    assertThat(WORDING.phrase(teamWith("LATE_WARD", "Dire's wards"), "x"))
        .isEqualTo("the team's ward near the fight went down too late");
    assertThat(WORDING.phrase(player("ROTATION_WASTED_BEFORE_FIGHT", "anything"), "Axe"))
        .isEqualTo("Axe had just wasted a rotation and missed the fight");
    assertThat(WORDING.phrase(player("ROTATION_DIED_BEFORE_FIGHT", "anything"), "Axe"))
        .isEqualTo("Axe died on a rotation just before the fight and missed it");
  }
}
