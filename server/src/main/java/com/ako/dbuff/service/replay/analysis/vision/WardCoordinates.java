package com.ako.dbuff.service.replay.analysis.vision;

/**
 * OpenDota's ward positions are map cells, roughly 64 to 192; hero tracks are world units, about
 * minus 8200 to 8200. The conversion {@code world = (cell - 128) * 128} was fitted on the four
 * fixture matches by comparing each ward placement with the placing hero's interpolated track
 * position at the placement time (134 placements with a track sample): a least-squares line gives
 * 126.4 units per cell and a zero at cell 128.1 on x, 126.8 and 128.4 on y, so the exact form was
 * taken. Against it the median distance is about 620 units (310 on x, 370 on y) and 90 percent are
 * within about 920: the hero stands up to the cast range away from the ward and its track is
 * sampled every 5 s, so this is the limit of what the comparison can tell, not the error of a ward
 * position itself. Cells are fractional in the logs, so the resolution is finer than a cell.
 */
public final class WardCoordinates {
  private static final double ORIGIN_CELL = 128;
  private static final double UNITS_PER_CELL = 128;

  private WardCoordinates() {}

  public static double toWorld(double cell) {
    return (cell - ORIGIN_CELL) * UNITS_PER_CELL;
  }

  public static double distance(double cellX, double cellY, double worldX, double worldY) {
    return Math.hypot(toWorld(cellX) - worldX, toWorld(cellY) - worldY);
  }
}
