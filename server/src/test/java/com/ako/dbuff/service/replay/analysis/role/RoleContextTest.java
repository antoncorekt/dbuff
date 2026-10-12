package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleContextTest {

  private static StageProfiler counting(AtomicInteger calls) {
    return new StageProfiler() {
      @Override
      public RoleStage stage() {
        return RoleStage.INTENT;
      }

      @Override
      public List<FunctionProfile> profile(List<RolePlayer> team) {
        calls.incrementAndGet();
        return List.of(FunctionProfile.UNKNOWN);
      }
    };
  }

  @Test
  void aProfiledContextWorksTheProfilesOutOnceForEveryReader() {
    AtomicInteger calls = new AtomicInteger();
    StageProfiler profiler = counting(calls);
    RoleContext context = RoleContext.profiledBy(RoleStage.INTENT, List.of(), profiler);

    context.profiles(profiler);
    context.profiles(profiler);

    assertThat(calls).hasValue(1);
  }

  @Test
  void aPlainContextAsksTheProfilerEachTime() {
    AtomicInteger calls = new AtomicInteger();
    StageProfiler profiler = counting(calls);
    RoleContext context = new RoleContext(RoleStage.INTENT, List.of());

    context.profiles(profiler);
    context.profiles(profiler);

    assertThat(calls).hasValue(2);
  }
}
