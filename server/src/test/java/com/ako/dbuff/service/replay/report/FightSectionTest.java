package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FightSectionTest {

  private final FightRepo fightRepo = mock(FightRepo.class);
  private final FightPlayerRepo partRepo = mock(FightPlayerRepo.class);
  private final FightAbilityUseRepo abilityRepo = mock(FightAbilityUseRepo.class);
  private final FightSection section = new FightSection(fightRepo, partRepo, abilityRepo);

  private ReplayReportInput input() {
    return new ReplayReportInput(1L, null, Set.of(128L), Map.of(128L, "Anton"), 3);
  }

  @Test
  void lines_sayWhyATrackedPlayerMissedABigFightAndWhichAbilityHeLeftUnused() {
    when(fightRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightDomain.builder()
                    .fightIndex(0)
                    .type("TEAMFIGHT")
                    .startSeconds(1390.0)
                    .radiantDeaths(4)
                    .direDeaths(1)
                    .winner("RADIANT")
                    .build(),
                FightDomain.builder()
                    .fightIndex(1)
                    .type("SKIRMISH")
                    .startSeconds(500.0)
                    .radiantDeaths(1)
                    .direDeaths(0)
                    .winner("RADIANT")
                    .build()));
    when(partRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightPlayerDomain.builder()
                    .fightIndex(0)
                    .playerSlot(128L)
                    .presence("ABSENT_FARMING")
                    .absenceReason("NO_TP")
                    .build(),
                FightPlayerDomain.builder()
                    .fightIndex(1)
                    .playerSlot(128L)
                    .presence("ABSENT_FAR")
                    .absenceReason("NO_TP")
                    .build()));
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      uses.add(
          FightAbilityUseDomain.builder()
              .fightIndex(i)
              .playerSlot(128L)
              .abilityName("faceless_void_chronosphere")
              .nonCastReason(i < 2 ? "READY_NOT_CAST" : null)
              .build());
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly(
            "**Fights**",
            "23:10 teamfight (lost, deaths 1 to 4): Anton was farming away from it, with no"
                + " teleport.",
            "Anton had Void Chronosphere ready and did not cast it in 2 of 3 fights.");
  }

  @Test
  void lines_areEmptyWhenNothingWentWrong() {
    when(fightRepo.findByMatchId(1L)).thenReturn(List.of());
    when(partRepo.findByMatchId(1L)).thenReturn(List.of());
    when(abilityRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(input())).isEmpty();
  }
}
