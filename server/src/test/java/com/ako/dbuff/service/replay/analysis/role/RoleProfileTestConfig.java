package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The profile beans a role analyzer test needs on top of the analyzer; the kits are its own. */
@TestConfiguration
@Import({
  ObservedProfiler.class,
  IntentStageProfiler.class,
  IntentProfileCalculator.class,
  RoleFitter.class,
  RoleOverrideFile.class,
  StageProfilers.class,
  RoleProfileProperties.class,
  ReplayAnalysisProperties.class
})
class RoleProfileTestConfig {}
