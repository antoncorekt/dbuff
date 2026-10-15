package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Optional;

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

  /**
   * Whether the section posts while its property is not set. Off unless a section says otherwise,
   * so a new section ships off until the config turns it on.
   */
  default boolean defaultEnabled() {
    return false;
  }

  /** Whether the section applies to the mode; a common one keeps the default. */
  default boolean supports(GameModeProfile mode) {
    return true;
  }

  /** Sections are posted in this order, lowest first. */
  int order();

  /** The lowest detail level of a channel that gets the section. */
  ReportDetail minimumDetail();

  /**
   * Whether {@link #lines} are finished Discord messages, each posted as it is in either report
   * format and never wrapped in a card or merged with other sections.
   */
  default boolean postsOwnMessages() {
    return false;
  }

  /** The text of the section, empty when there is nothing worth saying. */
  List<String> lines(ReplayReportInput input);

  /**
   * The section as a view for the renderers. A section that has not moved to views shows its lines
   * as bullets of an INFO card; a leading {@code **Title**} line becomes the card's title.
   */
  default Optional<SectionView> view(ReplayReportInput input) {
    List<String> lines = lines(input);
    if (lines.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(SectionView.ofLines(lines));
  }
}
