package com.ako.dbuff.service.replay.analysis.state;

import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.List;

/**
 * What one hero held and which of it was cooling down, rebuilt from the stored state changes so
 * that "did he own it", and "was it ready" can be asked for any second of the game.
 */
public final class HeroStateTimeline {

  /** One stored change, as the analysis needs it. */
  public record Change(double second, StateChangeKind kind, String name, double readyAtSeconds) {}

  private final List<Change> changes = new ArrayList<>();

  /** Changes must be added in time order. */
  public void add(Change change) {
    changes.add(change);
  }

  /** Whether the hero held at least one of the item at the time. */
  public boolean owns(String item, double second) {
    int count = 0;
    for (Change c : changes) {
      if (c.second() > second) {
        break;
      }
      if (c.name().equals(item)) {
        if (c.kind() == StateChangeKind.ITEM_ADDED) {
          count++;
        } else if (c.kind() == StateChangeKind.ITEM_REMOVED) {
          count--;
        }
      }
    }
    return count > 0;
  }

  /**
   * Whether the item or ability could be used at the time: no cooldown seen before then runs past
   * it. A thing never seen on cooldown counts as ready, so ask {@link #owns} first for items.
   */
  public boolean ready(String name, double second) {
    return cooldownLeft(name, second) == 0;
  }

  /** Seconds the last cooldown seen before the time still has to run, 0 when none does. */
  public double cooldownLeft(String name, double second) {
    double readyAt = Double.NEGATIVE_INFINITY;
    for (Change c : changes) {
      if (c.second() > second) {
        break;
      }
      if (c.kind() == StateChangeKind.COOLDOWN && c.name().equals(name)) {
        readyAt = c.readyAtSeconds();
      }
    }
    return Math.max(0, readyAt - second);
  }
}
