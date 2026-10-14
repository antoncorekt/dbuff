package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.ReplayStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
      public Object read(long matchId) {
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

    assertThat(assembler.assemble(5, null))
        .hasValueSatisfying(d -> assertThat(d).containsOnlyKeys("a", "b", "c"));
    assertThat(assembler.assemble(5, " ")).hasValueSatisfying(d -> assertThat(d).hasSize(3));
  }

  @Test
  void aSelection_runsOnlyThoseSectionsInDocumentOrder() {
    stored(5);

    assertThat(assembler.assemble(5, "c, a"))
        .hasValueSatisfying(d -> assertThat(d.keySet()).containsExactly("a", "c"));
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
  void anUnstoredMatch_hasNoDocument() {
    when(status.read(9)).thenReturn(Optional.empty());

    assertThat(assembler.assemble(9, null)).isEmpty();
  }

  @Test
  void twoReadersWithOneName_failAtStartup() {
    assertThatThrownBy(() -> new AnalysisAssembler(List.of(reader("a", 1), reader("a", 2)), status))
        .isInstanceOf(IllegalStateException.class);
  }
}
