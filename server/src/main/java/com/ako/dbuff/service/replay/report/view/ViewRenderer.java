package com.ako.dbuff.service.replay.report.view;

import java.util.List;

/** Turns a section's view into the messages that show it. */
public interface ViewRenderer {

  /** The messages of the view, empty when it has nothing to show. */
  List<ReportMessage> render(SectionView view);
}
