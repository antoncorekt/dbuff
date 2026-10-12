package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/**
 * Judges the composition of one team for the game modes it supports. A mode with its own rules adds
 * a bean; the analyzer does not change.
 */
public interface CompositionRules {
  boolean supports(GameModeProfile mode);

  /**
   * @param team the five players of one team at the stage
   * @return the tokens found; a token that needs a function no profile knew is never among them,
   *     the function is listed as unknown instead
   */
  CompositionVerdict evaluate(CompositionStage stage, List<TeamMember> team);
}
