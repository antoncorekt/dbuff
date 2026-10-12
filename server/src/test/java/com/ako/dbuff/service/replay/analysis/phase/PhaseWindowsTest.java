package com.ako.dbuff.service.replay.analysis.phase;

import com.ako.dbuff.service.replay.analysis.phase.PhaseWindows.Window;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseWindowsTest {

  @Test
  void of_cutsTheGameAtTheConfiguredMinutes() {
    assertThat(PhaseWindows.of(12, 25, 3000))
        .containsExactly(
            new Window("LANE", 0, 720),
            new Window("MID", 720, 1500),
            new Window("LATE", 1500, 3000));
  }

  @Test
  void of_leavesOutPhasesAShortGameNeverReaches() {
    assertThat(PhaseWindows.of(12, 25, 1000))
        .extracting(Window::name, Window::endSeconds)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("LANE", 720),
            org.assertj.core.groups.Tuple.tuple("MID", 1000));
    assertThat(PhaseWindows.of(12, 25, 0)).isEqualTo(List.of());
  }
}
