package com.ako.dbuff.dao.model;

/** How much of a match report a channel wants, from the Dotabuff link alone to everything. */
public enum ReportDetail {
  MINIMAL,
  STANDARD,
  FULL;

  /** Whether a channel at this level shows an analyzer that needs at least {@code minimum}. */
  public boolean includes(ReportDetail minimum) {
    return compareTo(minimum) >= 0;
  }
}
