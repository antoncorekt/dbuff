package com.ako.dbuff.service.replay.analysis.role;

import java.util.List;
import java.util.function.Supplier;

/**
 * One request to a {@link RoleResolver}.
 *
 * @param team the five players of one team, ordered by slot
 * @param profileSource the stage profiles of the team, worked out once for the resolver and the
 *     analyzer that stores them; null when the caller has none, then {@link #profiles} works them
 *     out
 */
public record RoleContext(
    RoleStage stage, List<RolePlayer> team, Supplier<List<FunctionProfile>> profileSource) {
  public RoleContext(RoleStage stage, List<RolePlayer> team) {
    this(stage, team, null);
  }

  /** A context whose profiles are worked out by the profiler on first use and kept. */
  public static RoleContext profiledBy(
      RoleStage stage, List<RolePlayer> team, StageProfiler profiler) {
    List<List<FunctionProfile>> kept = new java.util.ArrayList<>(1);
    return new RoleContext(
        stage,
        team,
        () -> {
          if (kept.isEmpty()) {
            kept.add(profiler.profile(team));
          }
          return kept.getFirst();
        });
  }

  /** The profiles of the team, from the source when there is one, else from the profiler. */
  public List<FunctionProfile> profiles(StageProfiler profiler) {
    return profileSource == null ? profiler.profile(team) : profileSource.get();
  }
}
