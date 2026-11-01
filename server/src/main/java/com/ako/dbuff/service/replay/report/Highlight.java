package com.ako.dbuff.service.replay.report;

/**
 * One candidate for the "What stood out" card.
 *
 * @param source the key of the source, as in {@code replay.report.highlights.<key>}
 * @param seconds game time of the moment, null when it has none
 * @param score how unusual it is, 0 to 1
 * @param text the finished sentence
 */
public record Highlight(String source, Double seconds, double score, String text) {

  /** 0 at or below {@code normal}, 1 at or above {@code extreme}, linear between. */
  public static double scale(double value, double normal, double extreme) {
    if (value <= normal) {
      return 0;
    }
    if (value >= extreme) {
      return 1;
    }
    return (value - normal) / (extreme - normal);
  }

  public static double scale(double value, ReplayReportProperties.Scale scale) {
    return scale(value, scale.getNormal(), scale.getExtreme());
  }
}
