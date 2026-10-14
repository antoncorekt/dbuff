package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;

/**
 * One part of the replay analysis posted in a match thread. A section reads the tables the replay
 * analyzers filled and writes plain statements with their game times; it never reads the replay.
 */
public interface ReplayReportSection {

  /**
   * The stable name of the section, unique among sections; {@code
   * replay.report.sections.<name>.enabled} switches it.
   */
  String name();

  /** Whether the section posts while its property is not set; a new section ships off. */
  default boolean defaultEnabled() {
    return true;
  }

  /** Whether the section applies to the mode; a common one keeps the default. */
  default boolean supports(GameModeProfile mode) {
    return true;
  }

  /** Sections are posted in this order, lowest first. */
  int order();

  /** The lowest detail level of a channel that gets the section. */
  ReportDetail minimumDetail();

  /** The text of the section, empty when there is nothing worth saying. */
  List<String> lines(ReplayReportInput input);
}
