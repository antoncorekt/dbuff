package com.ako.dbuff.service.replay.analysis;

import com.ako.dbuff.service.replay.mode.TestModes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayAnalysisServiceTest {

  private final ReplayAnalyzer good = mock(ReplayAnalyzer.class);
  private final ReplayAnalyzer broken = mock(ReplayAnalyzer.class);
  private final ReplayAnalysisService service = new ReplayAnalysisService(List.of(broken, good));

  @BeforeEach
  void analyzersApplyToEveryMode() {
    when(good.supports(any())).thenReturn(true);
    when(broken.supports(any())).thenReturn(true);
  }

  @Test
  void analyze_runsEveryAnalyzerAndNamesTheOnesThatFailed() {
    when(broken.name()).thenReturn("broken");
    doThrow(new IllegalStateException("boom")).when(broken).analyze(5L);

    List<String> failed = service.analyze(5L, TestModes.ABILITY_DRAFT, List.of());

    assertThat(failed).containsExactly("broken");
    verify(good).analyze(5L);
  }

  @Test
  void analyze_skipsAnAnalyzerWhoseInputExtractorFailed_soItsEarlierRowsSurvive() {
    when(good.requires()).thenReturn(java.util.Set.of("tracks"));

    List<String> failed = service.analyze(5L, TestModes.ABILITY_DRAFT, List.of("tracks"));

    assertThat(failed).doesNotContain("good");
    org.mockito.Mockito.verify(good, org.mockito.Mockito.never()).analyze(5L);
  }

  @Test
  void analyze_leavesOutAnAnalyzerThatDoesNotApplyToTheMode() {
    when(broken.supports(TestModes.ALL_PICK)).thenReturn(false);

    List<String> failed = service.analyze(5L, TestModes.ALL_PICK, List.of());

    assertThat(failed).isEmpty();
    verify(broken, org.mockito.Mockito.never()).analyze(5L);
    verify(good).analyze(5L);
  }

  @Test
  void deleteByMatchId_asksEveryAnalyzer() {
    service.deleteByMatchId(5L);

    verify(broken).deleteByMatchId(5L);
    verify(good).deleteByMatchId(5L);
  }
}
