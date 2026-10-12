package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftSectionTest {

  private final AbilityDraftPickRepo repo = mock(AbilityDraftPickRepo.class);
  private final DraftSection section = new DraftSection(repo);

  private AbilityDraftPickDomain pick(int order, long slot, String ability) {
    return AbilityDraftPickDomain.builder()
        .pickOrder(order)
        .teamPickIndex((order + 1) / 2)
        .playerSlot(slot)
        .abilityName(ability)
        .build();
  }

  @Test
  void lines_listWhatEachTrackedPlayerDraftedAndWhere() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                pick(12, 0, "sven_gods_strength"),
                pick(3, 0, "faceless_void_chronosphere"),
                pick(22, 0, "sven_storm_bolt"),
                pick(1, 128, "axe_culling_blade")));

    List<String> lines =
        section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Draft**",
            "Anton drafted Faceless Void Chronosphere (3rd), Sven Gods Strength (12th), Sven Storm"
                + " Bolt (22nd)");
  }

  @Test
  void lines_areEmptyWithoutADraft() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }
}
