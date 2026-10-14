package com.ako.dbuff.service.replay.report.view;

/** Discord message limits the report is cut to. */
public final class MessageLimits {
  /** Characters of a plain message. */
  public static final int PLAIN_TEXT = 2000;

  /** Total text of a Components V2 message; from third-party documentation, to confirm live. */
  public static final int COMPONENTS_TEXT = 4000;

  /** Components of a Components V2 message, nested ones included; from third-party docs. */
  public static final int COMPONENTS_COUNT = 40;

  private MessageLimits() {}
}
