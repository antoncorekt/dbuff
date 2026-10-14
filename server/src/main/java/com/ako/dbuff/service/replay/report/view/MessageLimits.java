package com.ako.dbuff.service.replay.report.view;

/** Discord message limits the report is cut to. */
public final class MessageLimits {
  /** Characters of a plain message. */
  public static final int PLAIN_TEXT = 2000;

  /** Total text of a Components V2 message; from third-party documentation, to confirm live. */
  public static final int COMPONENTS_TEXT = 4000;

  /** Components of a Components V2 message, nested ones included; from third-party docs. */
  public static final int COMPONENTS_COUNT = 40;

  /** Text kept free in every Components V2 message for the "left out" note. */
  public static final int NOTE_TEXT_RESERVE = 40;

  /** Components kept free in every Components V2 message for the "left out" note. */
  public static final int NOTE_COMPONENT_RESERVE = 1;

  /** The most characters of a card's title, and of its verdict; longer ones end in an ellipsis. */
  public static final int HEADER_PART = 200;

  private MessageLimits() {}
}
