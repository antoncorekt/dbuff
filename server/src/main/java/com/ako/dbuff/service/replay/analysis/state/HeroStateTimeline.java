package com.ako.dbuff.service.replay.analysis.state;

import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one hero held and which of it was cooling down, rebuilt from the stored state changes so
 * that "did he own it", and "was it ready" can be asked for any second of the game.
 */
public final class HeroStateTimeline {

  /** One stored change, as the analysis needs it. */
  public record Change(
      double second,
      StateChangeKind kind,
      String name,
      double readyAtSeconds,
      int level,
      int manaCost) {

    public Change(double second, StateChangeKind kind, String name, double readyAtSeconds) {
      this(second, kind, name, readyAtSeconds, 0, 0);
    }
  }

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

  /** The skill level reached by the time; 0 when it was never seen to rise. */
  public int level(String ability, double second) {
    int level = 0;
    for (Change c : changes) {
      if (c.second() > second) {
        break;
      }
      if (c.kind() == StateChangeKind.ABILITY_LEVEL && c.name().equals(ability)) {
        level = c.level();
      }
    }
    return level;
  }

  /** The mana cost last seen by the time; 0 when none was seen. */
  public int manaCost(String name, double second) {
    int cost = 0;
    for (Change c : changes) {
      if (c.second() > second) {
        break;
      }
      if (c.kind() == StateChangeKind.MANA_COST && c.name().equals(name)) {
        cost = c.manaCost();
      }
    }
    return cost;
  }

  /** Whether the item or ability was seen cooling down at any time, so it is meant to be used. */
  public boolean everOnCooldown(String name) {
    return changes.stream()
        .anyMatch(c -> c.kind() == StateChangeKind.COOLDOWN && c.name().equals(name));
  }

  /** The names of the abilities the hero was seen to learn, in the order first learned. */
  public Set<String> learnedAbilities() {
    Set<String> names = new LinkedHashSet<>();
    for (Change c : changes) {
      if (c.kind() == StateChangeKind.ABILITY_LEVEL) {
        names.add(c.name());
      }
    }
    return names;
  }

  /** The items held at the time, each name once. */
  public Set<String> itemsAt(double second) {
    Map<String, Integer> counts = new HashMap<>();
    for (Change c : changes) {
      if (c.second() > second) {
        break;
      }
      if (c.kind() == StateChangeKind.ITEM_ADDED) {
        counts.merge(c.name(), 1, Integer::sum);
      } else if (c.kind() == StateChangeKind.ITEM_REMOVED) {
        counts.merge(c.name(), -1, Integer::sum);
      }
    }
    Set<String> held = new TreeSet<>();
    counts.forEach(
        (name, n) -> {
          if (n > 0) {
            held.add(name);
          }
        });
    return held;
  }
}
