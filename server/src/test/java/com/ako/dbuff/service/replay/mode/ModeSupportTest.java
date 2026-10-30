package com.ako.dbuff.service.replay.mode;

import com.ako.dbuff.service.replay.AbilityDraftExtractor;
import com.ako.dbuff.service.replay.ReplayExtractor;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.ReplayReportSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModeSupportTest {

  @Test
  void aCommonExtractorAnalyzerAndSectionApplyToEveryMode() {
    ReplayExtractor extractor =
        new ReplayExtractor() {
          @Override
          public String name() {
            return "common";
          }

          @Override
          public void extract(
              com.ako.dbuff.service.replay.ReplayFacts facts,
              com.ako.dbuff.dao.model.MatchDomain match) {}

          @Override
          public void deleteByMatchId(long matchId) {}
        };
    ReplayAnalyzer analyzer = mock(ReplayAnalyzer.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    ReplayReportSection section = new DeathSection(null);

    for (GameModeProfile mode :
        new GameModeProfile[] {TestModes.ABILITY_DRAFT, TestModes.ALL_PICK}) {
      assertThat(extractor.supports(mode)).isTrue();
      assertThat(analyzer.supports(mode)).isTrue();
      assertThat(section.supports(mode)).isTrue();
    }
  }

  @Test
  void theDraftExtractorAndSectionApplyToAbilityDraftOnly() {
    AbilityDraftExtractor extractor =
        new AbilityDraftExtractor(null, null, null, new AbilityDraftProfile());
    DraftSection section =
        new DraftSection(null, null, new AbilityDraftProfile(), null, null, null, null);

    assertThat(extractor.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(extractor.supports(TestModes.ALL_PICK)).isFalse();
    assertThat(section.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(section.supports(TestModes.ALL_PICK)).isFalse();
  }
}
