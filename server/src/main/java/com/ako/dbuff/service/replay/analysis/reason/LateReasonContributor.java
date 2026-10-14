package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/**
 * Finds reasons for how one fight went from tables that are written after {@link
 * FightReasonAnalyzer} runs (vision, rotations). {@link LateFightReasonAnalyzer} stores them under
 * the contributor's own {@link #source()}, so a re-run replaces only these rows.
 */
public interface LateReasonContributor {
  /** The {@code source} of every reason this contributor gives. */
  String source();

  boolean supports(GameModeProfile mode);

  List<Reason> contribute(LateFightContext context);
}
