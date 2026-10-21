package com.ako.dbuff.service.replay.skillloop;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.service.replay.skillloop.SkillLoopReport.MatchData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLoopReportTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Windrunner", 128L, "Axe");

  private static MatchData data(
      String mode, List<MissedPickDomain> picks, List<BuildFitDomain> fits) {
    MatchDomain match = new MatchDomain();
    match.setId(1L);
    return new MatchData(1, match, mode, HEROES, picks, fits, name -> "Name of " + name);
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
        .evidence("Axe had no farming skill and died alone twice.")
        .build();
  }

  @Test
  void listsTheMissedPickAndTheBuildFitRow() {
    String report = SkillLoopReport.render(data("Ability Draft", List.of(pick()), List.of(fit())));

    assertThat(report)
        .contains("## Missed picks", "Total: 1", "Windrunner")
        .contains("The enemy took Name of a_cand at pick 9", "150% more damage")
        .contains(
            "## Build fit", "| Axe | BOTH |", "Axe had no farming skill and died alone twice.");
  }

  @Test
  void saysNotAbilityDraftInBothSectionsOfAnAllPickMatch() {
    String report = SkillLoopReport.render(data("All Pick", List.of(), List.of()));

    assertThat(report.split("not Ability Draft", -1)).hasSize(4);
    assertThat(report).contains("## Missed picks", "## Build fit");
  }
}
