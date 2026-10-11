package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.StateChange;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.StateFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.stringtables.StringTables;

/**
 * Notes what each hero holds and which of it is cooling down, at every sample, and keeps only the
 * changes. Cooldowns and times are kept in ticks until the game clock is known.
 */
final class StateRecorder implements SampleObserver {
  private static final int TICKS_PER_SECOND = 30;
  private static final int MAX_ITEMS = 20;
  private static final int MAX_ABILITIES = 40;
  private static final double MIN_COOLDOWN = 0.05;
  private static final double NEW_COOLDOWN_JUMP_TICKS = TICKS_PER_SECOND;

  private final Entities entities;
  private final StringTables stringTables;
  private final List<RawChange> changes = new ArrayList<>();
  private final Map<Integer, PlayerState> players = new HashMap<>();

  StateRecorder(Entities entities, StringTables stringTables) {
    this.entities = entities;
    this.stringTables = stringTables;
  }

  @Override
  public void sampled(long gridTick, List<Controllers.View> views) {
    StringTable names = stringTables.forName("EntityNames");
    for (Controllers.View view : views) {
      if (view.hero() == null) {
        continue;
      }
      PlayerState state = players.computeIfAbsent(view.playerId(), id -> new PlayerState());
      Map<String, Integer> items = new HashMap<>();
      Map<String, Integer> firstSlot = new HashMap<>();
      for (int i = 0; i < MAX_ITEMS; i++) {
        Entity item = entity(view.hero(), "m_hItems", i);
        if (item == null) {
          continue;
        }
        String name = name(names, item);
        items.merge(name, 1, Integer::sum);
        firstSlot.putIfAbsent(name, i);
        cooldown(state, view.playerId(), gridTick, "item" + i + name, name, i, item);
      }
      diffItems(state, view.playerId(), gridTick, items, firstSlot);
      for (int i = 0; i < MAX_ABILITIES; i++) {
        Entity ability = entity(view.hero(), "m_vecAbilities", i);
        if (ability == null) {
          continue;
        }
        String name = name(names, ability);
        cooldown(state, view.playerId(), gridTick, "ability" + name, name, i, ability);
        level(state, view.playerId(), gridTick, name, i, ability);
      }
    }
  }

  /** The changes with times counted from 0:00. */
  StateFacts result(long clockStartTick) {
    List<StateChange> result = new ArrayList<>(changes.size());
    for (RawChange c : changes) {
      result.add(
          new StateChange(
              c.playerId(),
              (c.tick() - clockStartTick) / (double) TICKS_PER_SECOND,
              c.kind(),
              c.name(),
              c.slot(),
              c.kind() == StateChangeKind.COOLDOWN
                  ? (c.readyAtTick() - clockStartTick) / (double) TICKS_PER_SECOND
                  : 0,
              c.length(),
              c.level()));
    }
    return new StateFacts(List.copyOf(result));
  }

  private void diffItems(
      PlayerState state,
      int playerId,
      long tick,
      Map<String, Integer> now,
      Map<String, Integer> firstSlot) {
    for (var entry : now.entrySet()) {
      int before = state.items.getOrDefault(entry.getKey(), 0);
      for (int n = before; n < entry.getValue(); n++) {
        changes.add(
            new RawChange(
                playerId,
                tick,
                StateChangeKind.ITEM_ADDED,
                entry.getKey(),
                firstSlot.get(entry.getKey()),
                0,
                0,
                0));
      }
    }
    for (var entry : state.items.entrySet()) {
      int after = now.getOrDefault(entry.getKey(), 0);
      for (int n = after; n < entry.getValue(); n++) {
        changes.add(
            new RawChange(
                playerId, tick, StateChangeKind.ITEM_REMOVED, entry.getKey(), -1, 0, 0, 0));
      }
    }
    state.items = now;
  }

  private void cooldown(
      PlayerState state,
      int playerId,
      long tick,
      String key,
      String name,
      int slot,
      Entity entity) {
    double remaining = number(entity, "m_fCooldown");
    if (remaining <= MIN_COOLDOWN) {
      return;
    }
    double readyAt = tick + remaining * TICKS_PER_SECOND;
    Double previous = state.readyAt.put(key, readyAt);
    if (previous == null || readyAt > previous + NEW_COOLDOWN_JUMP_TICKS) {
      changes.add(
          new RawChange(
              playerId,
              tick,
              StateChangeKind.COOLDOWN,
              name,
              slot,
              readyAt,
              number(entity, "m_flCooldownLength"),
              0));
    }
  }

  private void level(
      PlayerState state, int playerId, long tick, String name, int slot, Entity ability) {
    int level = (int) number(ability, "m_iLevel");
    Integer previous = state.levels.put(name, level);
    if (previous != null && level > previous) {
      changes.add(
          new RawChange(playerId, tick, StateChangeKind.ABILITY_LEVEL, name, slot, 0, 0, level));
    }
  }

  private Entity entity(Entity hero, String list, int index) {
    String key = list + "." + String.format(Locale.ROOT, "%04d", index);
    if (!hero.hasProperty(key) || !(hero.getProperty(key) instanceof Number handle)) {
      return null;
    }
    return entities.getByHandle(handle.intValue());
  }

  private static String name(StringTable names, Entity entity) {
    Object index =
        entity.hasProperty("m_pEntity.m_nameStringTableIndex")
            ? entity.getProperty("m_pEntity.m_nameStringTableIndex")
            : null;
    if (index instanceof Number n && n.intValue() >= 0) {
      return names.getNameByIndex(n.intValue());
    }
    return entity.getDtClass().getDtName();
  }

  private static double number(Entity entity, String property) {
    return entity.hasProperty(property) && entity.getProperty(property) instanceof Number n
        ? n.doubleValue()
        : 0;
  }

  private record RawChange(
      int playerId,
      long tick,
      StateChangeKind kind,
      String name,
      int slot,
      double readyAtTick,
      double length,
      int level) {}

  private static final class PlayerState {
    private Map<String, Integer> items = new HashMap<>();
    private final Map<String, Double> readyAt = new HashMap<>();
    private final Map<String, Integer> levels = new HashMap<>();
  }
}
