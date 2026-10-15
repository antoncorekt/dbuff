package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.Optional;

/** The draft board of one game mode; a mode adds a bean, the section does not change. */
public interface DraftBoard {
  /** The OpenDota game mode id the board is for. */
  long gameModeId();

  /** The board of the match, empty when the match has no draft data for it. */
  Optional<SectionView> view(ReplayReportInput input);
}
