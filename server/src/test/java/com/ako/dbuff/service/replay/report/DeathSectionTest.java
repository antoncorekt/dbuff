package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeathSectionTest {

  private final DeathAnalysisRepo repo = mock(DeathAnalysisRepo.class);
  private final DeathSection section = new DeathSection(repo);

  private DeathAnalysisDomain death(long slot, double second, String reason, String killer) {
    return DeathAnalysisDomain.builder()
        .playerSlot(slot)
        .deathSeconds(second)
        .reason(reason)
        .killerName(killer)
        .disableSeconds(4.2)
        .damageLastThreeSeconds(1800L)
        .build();
  }

  @Test
  void lines_explainTheWorthMentioningDeathsOfTheTrackedPlayersOnly() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                death(0, 2470, "BKB_READY_UNUSED", "npc_dota_hero_lycan"),
                death(0, 600, "NO_BKB", "npc_dota_hero_axe"),
                death(0, 1500, "DISABLE_CHAIN", "npc_dota_hero_sven"),
                death(5, 700, "BKB_READY_UNUSED", "npc_dota_hero_axe")));

    List<String> lines =
        section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Deaths**",
            "25:00 Anton died to Sven: disabled for 4.2 s in the last seconds",
            "41:10 Anton died to Lycan: had Black King Bar ready and did not use it");
  }

  @Test
  void lines_areEmptyWithoutAnythingToExplain() {
    when(repo.findByMatchId(1L)).thenReturn(List.of(death(0, 600, "OTHER", "")));

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }
}
