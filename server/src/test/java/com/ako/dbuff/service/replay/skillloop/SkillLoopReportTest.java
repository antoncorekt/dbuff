package com.ako.dbuff.service.replay.skillloop;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.service.replay.skillloop.SkillLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLoopReportTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Windrunner", 128L, "Axe");

  private static MatchData data(
      String mode,
      List<MissedPickDomain> picks,
      List<BuildFitDomain> fits,
      List<SkillEffectDomain> effects) {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    return new MatchData(1, match, mode, HEROES, picks, fits, effects, name -> "Name of " + name);
  }

  private static MissedPickDomain pick() {
    return MissedPickDomain.builder()
        .playerSlot(0L)
        .takenAbility("a_taken")
        .takenTurn(5)
        .candidateAbility("a_cand")
        .candidateTurn(9)
        .basis("DAMAGE")
        .ratio(2.5)
        .candidateWonFights(3)
        .enemyWonFights(4)
        .takenWonFights(1)
        .build();
  }

  private static BuildFitDomain fit() {
    return BuildFitDomain.builder()
        .playerSlot(128L)
        .stage("BOTH")
        .role(1)
        .missing("FARM_PRIORITY,DAMAGE")
        .stated("FARM_PRIORITY")
        .verdict("GAP")
        .unknown("ESCAPE")
        .evidence("Axe had no farming skill and died alone twice.")
        .build();
  }

  @Test
  void listsTheMissedPickAndTheBuildFitRow() {
    String report =
        SkillLoopReport.render(data("Ability Draft", List.of(pick()), List.of(fit()), List.of()));

    assertThat(report)
        .contains("## Missed picks", "Total: 1", "Windrunner")
        .contains("The enemy took Name of a_cand at pick 9", "150% more damage")
        .contains(
            "## Build fit",
            "| verdict | missing | stated | unknown |",
            "| Axe | BOTH | carry | GAP | farm priority, damage | farm priority | escape |",
            "Axe had no farming skill and died alone twice.");
  }

  @Test
  void saysNotAbilityDraftInBothSectionsOfAnAllPickMatch() {
    String report = SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of()));

    assertThat(report.split("not Ability Draft", -1)).hasSize(4);
    assertThat(report).contains("## Missed picks", "## Build fit");
  }

  @Test
  void listsTheSkillEffectsWithCountsAndEveryDetailLine() {
    String details =
        IntStream.range(0, 7)
            .mapToObj(
                i ->
                    "{\"second\":%d,\"target\":\"Axe\",\"outcome\":\"SAVE\",\"numbers\":{\"damage\":900.4}}"
                        .formatted(600 + i))
            .collect(Collectors.joining(",", "[", "]"));
    SkillEffectDomain effect =
        SkillEffectDomain.builder()
            .playerSlot(0L)
            .entryKey("false_promise")
            .castCount(7)
            .figures("{\"SAVE\":7,\"IMPACT\":0,\"NOT_COUNTED\":0,\"MISSED\":1}")
            .details(details)
            .build();

    String report = SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of(effect)));

    assertThat(report)
        .contains(
            "## Skill effects",
            "- Windrunner, false_promise: 7 casts; SAVE 7, IMPACT 0, NOT_COUNTED 0, MISSED 1",
            "SAVE 10:00 on Axe (damage 900)");
    assertThat(report.split("SAVE 10:0", -1)).hasSize(8);
  }

  @Test
  void printsTheLineTheMatchReportWouldSayAboveTheDetailsAndNoneForNothingToSay() {
    SkillEffectDomain said =
        SkillEffectDomain.builder()
            .playerSlot(0L)
            .entryKey("false_promise")
            .castCount(5)
            .figures("{\"SAVE\":3,\"IMPACT\":2,\"MISSED\":2}")
            .build();
    SkillEffectDomain silent =
        SkillEffectDomain.builder()
            .playerSlot(0L)
            .entryKey("ice_shards")
            .castCount(5)
            .figures("{\"NOT_COUNTED\":5}")
            .build();

    String report =
        SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of(said, silent)));

    assertThat(report)
        .contains(
            "  - Report line: Windrunner's False Promise saved 3 allies, 2 more died but dealt"
                + " damage; 2 chances missed.");
    assertThat(report.split("Report line:", -1)).hasSize(2);
  }

  @Test
  void saysNothingWasCastWhenThereAreNoSkillEffects() {
    assertThat(SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of())))
        .contains("## Skill effects", "No catalogued skill was cast or learned.");
  }

  @Test
  void printsFortyCastLinesAndOneLineForTheRest() {
    String details =
        IntStream.range(0, 45)
            .mapToObj(
                i ->
                    "{\"second\":%d,\"target\":\"Axe\",\"outcome\":\"SAVE\",\"numbers\":{}}"
                        .formatted(600 + i))
            .collect(Collectors.joining(",", "[", "]"));
    SkillEffectDomain effect =
        SkillEffectDomain.builder()
            .playerSlot(0L)
            .entryKey("false_promise")
            .castCount(45)
            .figures("{\"SAVE\":45}")
            .details(details)
            .build();

    String report = SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of(effect)));

    assertThat(report.split("  - SAVE ", -1)).hasSize(41);
    assertThat(report).contains("  - ... 5 more casts");
  }

  @Test
  void printsTheReportLineBulletUnderTheCountsForAHookRowAndAPromiseRow() {
    SkillEffectDomain hook =
        SkillEffectDomain.builder()
            .playerSlot(0L)
            .entryKey("meat_hook")
            .castCount(19)
            .figures("{\"SEEN_HIT\":7,\"SEEN_MISS\":5,\"accuracy\":58}")
            .build();
    SkillEffectDomain promise =
        SkillEffectDomain.builder()
            .playerSlot(128L)
            .entryKey("false_promise")
            .castCount(5)
            .figures("{\"SAVE\":3}")
            .build();

    String report =
        SkillLoopReport.render(data("All Pick", List.of(), List.of(), List.of(hook, promise)));
    List<String> lines = List.of(report.split("\n"));

    int hookAt =
        lines.indexOf("- Windrunner, meat_hook: 19 casts; SEEN_HIT 7, SEEN_MISS 5, accuracy 58");
    assertThat(hookAt).isNotNegative();
    assertThat(lines.get(hookAt + 1)).startsWith("  - Report line: ").contains("hooks");
    int promiseAt = lines.indexOf("- Axe, false_promise: 5 casts; SAVE 3");
    assertThat(promiseAt).isNotNegative();
    assertThat(lines.get(promiseAt + 1)).startsWith("  - Report line: ").contains("False Promise");
  }
}
