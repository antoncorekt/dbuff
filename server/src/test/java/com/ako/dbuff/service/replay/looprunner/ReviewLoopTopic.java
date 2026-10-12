package com.ako.dbuff.service.replay.looprunner;

import java.nio.file.Path;
import java.util.List;

/** What one review topic adds to the loop: its match list, its report and the tables it diffs. */
public interface ReviewLoopTopic {
  /** The value of {@code -Ploop.topic}. */
  String name();

  Path matchList();

  /** Where the {@code round-NN} directories go. */
  Path outputDir();

  List<ReviewLoopDiff.Section> sections();

  /** The report of a match whose analysis is stored; {@link #matchList()} ids only. */
  String render(long matchId);
}
