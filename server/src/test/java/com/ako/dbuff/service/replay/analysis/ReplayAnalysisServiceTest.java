package com.ako.dbuff.service.replay.analysis;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayAnalysisServiceTest {

  private final ReplayAnalyzer good = mock(ReplayAnalyzer.class);
  private final ReplayAnalyzer broken = mock(ReplayAnalyzer.class);
  private final ReplayAnalysisService service = new ReplayAnalysisService(List.of(broken, good));

  @Test
  void analyze_runsEveryAnalyzerAndNamesTheOnesThatFailed() {
    when(broken.name()).thenReturn("broken");
    doThrow(new IllegalStateException("boom")).when(broken).analyze(5L);

    List<String> failed = service.analyze(5L, List.of());

    assertThat(failed).containsExactly("broken");
    verify(good).analyze(5L);
  }

  @Test
  void analyze_skipsAnAnalyzerWhoseInputExtractorFailed_soItsEarlierRowsSurvive() {
    when(good.requires()).thenReturn(java.util.Set.of("tracks"));

    List<String> failed = service.analyze(5L, List.of("tracks"));

    assertThat(failed).doesNotContain("good");
    org.mockito.Mockito.verify(good, org.mockito.Mockito.never()).analyze(5L);
  }

  @Test
  void deleteByMatchId_asksEveryAnalyzer() {
    service.deleteByMatchId(5L);

    verify(broken).deleteByMatchId(5L);
    verify(good).deleteByMatchId(5L);
  }
}
