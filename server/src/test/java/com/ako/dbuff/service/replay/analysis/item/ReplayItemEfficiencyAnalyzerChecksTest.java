package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyInputs.MatchInputs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayItemEfficiencyAnalyzerChecksTest {
  private final ItemEfficiencyInputs inputs = mock(ItemEfficiencyInputs.class);
  private final ItemEfficiencyRepo repo = mock(ItemEfficiencyRepo.class);

  private ReplayItemEfficiencyAnalyzer analyzer(String entries) {
    String json = "{\"status\": \"s\", \"entries\": [" + entries + "]}";
    when(inputs.load(1)).thenReturn(new MatchInputs(100, List.of()));
    return new ReplayItemEfficiencyAnalyzer(
        new ItemEfficiencyFile(
            () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json"),
        List.of(new MagicStickEvaluator()),
        inputs,
        key -> java.util.Optional.empty(),
        repo);
  }

  @Test
  void anEntryWithNoEvaluatorForItsKindStopsTheRunNamingTheEntry() {
    ReplayItemEfficiencyAnalyzer analyzer =
        analyzer("{\"key\": \"a\", \"kind\": \"nope\", \"items\": [\"x\"]}");

    assertThatThrownBy(() -> analyzer.analyze(1))
        .hasMessageContaining("'a'")
        .hasMessageContaining("nope");
  }

  @Test
  void aParameterTheEvaluatorDoesNotKnowIsCaughtBeforeAnythingIsReplaced() {
    ReplayItemEfficiencyAnalyzer analyzer =
        analyzer(
            "{\"key\": \"a\", \"kind\": \"magic-stick\", \"items\": [\"x\"],"
                + " \"parameters\": {\"saveGolds\": 1}}");

    assertThatThrownBy(() -> analyzer.analyze(1))
        .hasMessageContaining("unknown parameter 'saveGolds'");
    verify(repo, org.mockito.Mockito.never()).deleteByMatchId(1L);
  }

  @Test
  void aMatchWithoutHeroesStoresNothingAndTheAnalyzerIsModeAgnostic() {
    ReplayItemEfficiencyAnalyzer analyzer =
        analyzer("{\"key\": \"a\", \"kind\": \"magic-stick\", \"items\": [\"x\"]}");

    analyzer.analyze(1);

    assertThat(analyzer.supports(null)).isTrue();
    assertThat(analyzer.requires())
        .contains("players", "tracks", "state-changes", "combat-log", "activity", "creep-kills");
    verify(repo).deleteByMatchId(1L);
  }
}
