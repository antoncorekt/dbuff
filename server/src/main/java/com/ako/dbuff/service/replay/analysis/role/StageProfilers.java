package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The {@link StageProfiler} of each {@link RoleStage}. */
@Component
@RequiredArgsConstructor
public class StageProfilers {
  private final List<StageProfiler> profilers;

  public StageProfiler of(RoleStage stage) {
    return profilers.stream()
        .filter(p -> p.stage() == stage)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No profiler for the stage " + stage));
  }
}
