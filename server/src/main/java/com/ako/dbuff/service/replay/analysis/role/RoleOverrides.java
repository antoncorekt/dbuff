package com.ako.dbuff.service.replay.analysis.role;

import java.util.Map;

/**
 * What is known about a skill or a hero that OpenDota's constants cannot express: a function value
 * the intent profile adds on top of what the rules read from the constants.
 */
public interface RoleOverrides {
  RoleOverrides NONE =
      new RoleOverrides() {
        @Override
        public Map<RoleFunction, Double> forAbility(String name) {
          return Map.of();
        }

        @Override
        public Map<RoleFunction, Double> forHero(long heroId) {
          return Map.of();
        }
      };

  /** Values 0 to 1 per function for a regular skill; empty when the skill has no override. */
  Map<RoleFunction, Double> forAbility(String name);

  /** Values 0 to 1 per function for the hero; empty when the hero has no override. */
  Map<RoleFunction, Double> forHero(long heroId);
}
