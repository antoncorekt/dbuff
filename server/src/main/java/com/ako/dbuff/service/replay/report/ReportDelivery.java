package com.ako.dbuff.service.replay.report;

/** What came of an attempt to post a match's replay report into a channel's thread. */
public enum ReportDelivery {
  POSTED("posted"),
  REPORT_SWITCHED_OFF("the replay report is switched off on the server"),
  NOT_STORED("no replay analysis is stored for this match"),
  NO_CHANNEL("the channel is not set up"),
  REPORT_OFF_FOR_CHANNEL(
      "the replay report is off for this channel (/dbuff settings replay_report)"),
  MINIMAL_DETAIL("the channel's detail level is minimal (/dbuff settings detail)"),
  ALREADY_POSTED("it was already posted"),
  QUIET_HOURS("it is quiet hours"),
  NO_THREAD("this match has no thread on record"),
  NO_SECTIONS("no report section applies to this match with the current settings"),
  THREAD_NOT_FOUND("Discord could not find the thread"),
  FAILED("something went wrong, see the log");

  private final String reason;

  ReportDelivery(String reason) {
    this.reason = reason;
  }

  /** A plain-words reason, meant to follow "not posted: ". */
  public String reason() {
    return reason;
  }
}
