package com.ako.dbuff.service.replay;

import java.util.List;

/** The buybacks the combat log records. */
public final class Buybacks {
  /** A buyback is paid for while dead, so it follows a death by no more than the respawn time. */
  public static final double LONGEST_AFTER_DEATH_SECONDS = 150;

  private Buybacks() {}

  /**
   * @param player the player index 0-9, Radiant first, as the combat log's value field gives it
   */
  public record Buyback(double second, int player) {}

  public static List<Buyback> of(List<CombatEvent> events) {
    return events.stream()
        .filter(e -> e.kind() == CombatEventKind.BUYBACK)
        .map(e -> new Buyback(e.gameSecond(), e.value()))
        .toList();
  }
}
