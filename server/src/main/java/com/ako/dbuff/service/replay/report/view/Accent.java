package com.ako.dbuff.service.replay.report.view;

/** The colour of a section's card: green for a good outcome, red for a bad one. */
public enum Accent {
  WIN(0x2ECC71),
  LOSS(0xE74C3C),
  NEUTRAL(0x95A5A6),
  INFO(0x3498DB);

  private final int rgb;

  Accent(int rgb) {
    this.rgb = rgb;
  }

  public int rgb() {
    return rgb;
  }
}
