package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.analysis.skill.SkillCatalogFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillEffectsSectionTest {
  private static final AbilityLabels LABELS =
      name -> name.equals("oracle_false_promise") ? "False Promise" : "Ice Shards";

  private final SkillEffectRepo repo = mock(SkillEffectRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final SkillEffectsSection section =
      new SkillEffectsSection(repo, new SkillCatalogFile("replay/skills.yaml"), heroNames, LABELS);

  private static SkillEffectDomain row(long slot, String key, int casts, String figures) {
    return SkillEffectDomain.builder()
        .matchId(1L)
        .playerSlot(slot)
        .entryKey(key)
        .castCount(casts)
        .figures(figures)
        .build();
  }

  private ReplayReportInput input(Set<Long> focus, int max) {
    return new ReplayReportInput(1L, null, focus, Map.of(), max);
  }

  private void stored(SkillEffectDomain... rows) {
    when(repo.findByMatchId(1L)).thenReturn(List.of(rows));
    when(heroNames.iconBySlot(1L)).thenReturn(Map.of(0L, "Pudge", 1L, "Doom", 2L, "Oracle"));
  }

  @Test
  void isOffByDefaultAndSitsBetweenSkillPicksAndOpening() {
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.name()).isEqualTo("skill-effects");
    assertThat(section.order()).isEqualTo(14);
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
  }

  @Test
  void writesOneLineForEachFocusHeroAndEntryInSlotThenCatalogOrder() {
    stored(
        row(1, "ice_shards", 26, "{\"TRAP\":4,\"MISS\":3,\"NOT_COUNTED\":19}"),
        row(0, "meat_hook", 19, "{\"SEEN_HIT\":7,\"SEEN_MISS\":5,\"accuracy\":58}"),
        row(2, "false_promise", 10, "{\"SAVE\":3}"));

    assertThat(section.lines(input(Set.of(0L, 1L), 10)))
        .containsExactly(
            "Pudge: 12 hooks at seen enemies, 7 hit (58%).",
            "Doom's Ice Shards trapped at least 4 enemies in 26 casts; 3 casts missed a seen enemy"
                + " (the shard centre is estimated).");
  }

  @Test
  void keepsAtMostTheCapInTotal() {
    stored(
        row(0, "meat_hook", 5, "{\"SEEN_HIT\":1}"),
        row(1, "ice_shards", 5, "{\"TRAP\":1}"),
        row(2, "false_promise", 5, "{\"SAVE\":1}"));

    assertThat(section.lines(input(Set.of(0L, 1L, 2L), 2))).hasSize(2);
  }

  @Test
  void skipsUnreadableFiguresRowsWithNothingToSayAndMatchesWithNoRows() {
    stored(row(0, "meat_hook", 5, "not json"), row(1, "ice_shards", 5, "{\"NOT_COUNTED\":5}"));
    assertThat(section.lines(input(Set.of(0L, 1L), 3))).isEmpty();
    assertThat(section.view(input(Set.of(0L, 1L), 3))).isEmpty();

    when(repo.findByMatchId(1L)).thenReturn(List.of());
    assertThat(section.lines(input(Set.of(0L), 3))).isEmpty();
  }
}
