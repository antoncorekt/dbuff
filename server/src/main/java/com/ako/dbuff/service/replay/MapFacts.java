package com.ako.dbuff.service.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * The fixed parts of the map as the replay lists them: buildings with their positions and the
 * jungle camps with their boxes. Coordinates are world units.
 */
public record MapFacts(List<Building> buildings, List<Camp> camps) {

  /** A tower, barracks, fort, outpost or lotus pool; the name tells which. */
  public record Building(String name, int team, int x, int y) {}

  /** A neutral camp; {@code type} is the game's camp size class. */
  public record Camp(
      String name, int type, int minX, int minY, int maxX, int maxY, int originX, int originY) {}

  /**
   * A short fingerprint of the building layout. Two games on the same map share it, so stored
   * results can say which layout they were worked out on, and a patch that moves a tower changes
   * it.
   */
  public String version() {
    StringBuilder text = new StringBuilder();
    buildings.stream()
        .sorted(Comparator.comparing(Building::name))
        .forEach(b -> text.append(b.name()).append(b.x()).append(',').append(b.y()).append(';'));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(text.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 6);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
