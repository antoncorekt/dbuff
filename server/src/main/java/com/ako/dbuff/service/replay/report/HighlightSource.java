package com.ako.dbuff.service.replay.report;

import java.util.List;

/** Something that can tell which of its facts about a match are unusual. */
public interface HighlightSource {
  List<Highlight> highlights(ReplayReportInput input);
}
