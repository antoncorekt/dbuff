package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/**
 * Finds reasons for how one fight went. A common contributor supports every mode; a mode with
 * reasons of its own adds a bean, and {@link FightReasonAnalyzer} does not change.
 */
public interface ReasonContributor {
  boolean supports(GameModeProfile mode);

  List<Reason> contribute(FightContext context);
}
