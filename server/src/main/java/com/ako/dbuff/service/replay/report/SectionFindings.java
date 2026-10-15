package com.ako.dbuff.service.replay.report;

import java.util.List;

/**
 * A section whose statements are of different kinds. The AI facts take their kind from the finding
 * instead of from the section.
 */
public interface SectionFindings {
  List<Finding> findings(ReplayReportInput input);
}
