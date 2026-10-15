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

  /** Text of a Components V2 message that report cards may fill; the rest is for the note. */
  public static final int CARD_TEXT = COMPONENTS_TEXT - NOTE_TEXT_RESERVE;

  /** Components of a Components V2 message that report cards may fill. */
  public static final int CARD_COMPONENTS = COMPONENTS_COUNT - NOTE_COMPONENT_RESERVE;

  /** The container of a card. */
  public static final int CONTAINER_COMPONENTS = 1;

  /** The header text block of a card and the divider under it. */
  public static final int HEADER_COMPONENTS = 2;

  /** The media gallery of a card with an image and the one item it holds. */
  public static final int IMAGE_COMPONENTS = 2;

  /** The longest page marker, kept free in the header of a card that may be split. */
  public static final String PAGE_MARKER_RESERVE = " (99/99)";

  /** The most characters of a card's title, and of its verdict; longer ones end in an ellipsis. */
  public static final int HEADER_PART = 200;

  private MessageLimits() {}
}
