package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisAssemblerTest {
  private final RunStatusReader status = mock(RunStatusReader.class);
  private final AnalysisAssembler assembler =
      new AnalysisAssembler(List.of(reader("a", 1), reader("b", 2), reader("c", 3)), status);

  private static AnalysisSectionReader reader(String name, Object value) {
    return new AnalysisSectionReader() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public Object read(long matchId, RunStatus run) {
        return value;
      }
    };
  }

  private void stored(long matchId) {
    when(status.read(matchId))
        .thenReturn(Optional.of(new RunStatus(matchId, ReplayStatus.DONE, 1, null, null)));
  }

  @Test
  void noSelection_runsEverySectionInReaderOrder() {
    stored(5);

    assertThat(assembler.assemble(5, null).keySet()).containsExactly("a", "b", "c");
  }

  @Test
  void aSelection_runsOnlyThoseSectionsInDocumentOrder() {
    stored(5);

    assertThat(assembler.assemble(5, "c, a").keySet()).containsExactly("a", "c");
  }

  @Test
  void anUnknownSection_isRejectedNamingTheValidOnes() {
    stored(5);

    assertThatThrownBy(() -> assembler.assemble(5, "a,x"))
        .isInstanceOf(UnknownSectionException.class)
        .hasMessageContaining("[x]")
        .hasMessageContaining("[a, b, c]");
  }

  @Test
  void anEmptyEntry_isRejected() {
    stored(5);

    for (String bad : List.of("a,,b", ",", "", " ", "a,")) {
      assertThatThrownBy(() -> assembler.assemble(5, bad))
          .as("'%s'", bad)
          .isInstanceOf(InvalidSectionsException.class)
          .hasMessageContaining("empty entry");
    }
  }

  @Test
  void anUnstoredMatch_isNotFound() {
    when(status.read(9)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> assembler.assemble(9, null))
        .isInstanceOf(MatchNotStoredException.class);
  }

  @Test
  void theStatusIsReadOnce_andEverySectionGetsThatSameRun() {
    stored(5);
    List<RunStatus> seen = new ArrayList<>();
    AnalysisAssembler spy =
        new AnalysisAssembler(
            List.of(
                new AnalysisSectionReader() {
                  public String name() {
                    return "a";
                  }

                  public Object read(long matchId, RunStatus run) {
                    seen.add(run);
                    return run;
                  }
                },
                new AnalysisSectionReader() {
                  public String name() {
                    return "b";
                  }

                  public Object read(long matchId, RunStatus run) {
                    seen.add(run);
                    return run;
                  }
                }),
            status);

    spy.assemble(5, null);

    verify(status, times(1)).read(5);
    assertThat(seen).hasSize(2).containsOnly(seen.get(0));
  }

  @Test
  void twoReadersWithOneName_failAtStartup() {
    assertThatThrownBy(() -> new AnalysisAssembler(List.of(reader("a", 1), reader("a", 2)), status))
        .isInstanceOf(IllegalStateException.class);
  }
}
