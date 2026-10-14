package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.ReplayStatus;

/** What one replay of the load test cost and produced. */
record LoadTestRun(
    long matchId,
    String mode,
    long minutes,
    double seconds,
    long liveMb,
    long peakMb,
    long peakOldMb,
    String status,
    long rows,
    String note) {

  boolean passed() {
    return status.equals(ReplayStatus.DONE.name());
  }
}
