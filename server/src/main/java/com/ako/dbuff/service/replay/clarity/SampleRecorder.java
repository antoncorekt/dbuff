package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.MinuteStat;
import com.ako.dbuff.service.replay.SampleFacts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.Entities;

/**
 * Samples every player every five game seconds from the start of the pre-game phase, and their
 * cumulative counters once a game minute. Ticks are 30 a second, so the grid is derived from the
 * tick where the pre-game phase begins, 90 seconds before 0:00.
 */
final class SampleRecorder {
  private static final int STEP_TICKS = 150;
  private static final int STEP_SECONDS = 5;
  private static final int TICKS_PER_SECOND = 30;
  private static final int PRE_GAME_TICKS = 2700;
  private static final int MINUTE_TICKS = 1800;
  private static final int MINUTE_OFFSET_TICKS = 900;
  private static final int PLAYERS_PER_TEAM = 5;
  private static final int MISSING = -1;
  private static final String RADIANT_DATA = "CDOTA_DataRadiant";
  private static final String DIRE_DATA = "CDOTA_DataDire";
  private static final String[] RELIABLE_GOLD = goldProperties("m_iReliableGold");
  private static final String[] UNRELIABLE_GOLD = goldProperties("m_iUnreliableGold");

  private final Entities entities;
  private final Map<Integer, Series> series = new TreeMap<>();
  private final List<RawSnapshot> snapshots = new ArrayList<>();
  private long anchorTick = -1;

  /** The tick of 0:00 as far as is known: assumed from the pre-game phase, then seen. */
  private long clockOriginTick;

  private long nextGridTick;
  private boolean stopped;
  private final Map<String, Entity> teamEntities = new HashMap<>();
  private final List<SampleObserver> observers = new ArrayList<>();

  SampleRecorder(Entities entities) {
    this.entities = entities;
  }

  void observe(SampleObserver observer) {
    observers.add(observer);
  }

  void preGameStarted(long tick) {
    if (anchorTick < 0) {
      anchorTick = tick;
      clockOriginTick = tick + PRE_GAME_TICKS;
      nextGridTick = tick;
    }
  }

  /** A replay without a recorded pre-game phase is sampled from 0:00. */
  void gameStarted(long tick) {
    if (anchorTick < 0) {
      anchorTick = tick;
      nextGridTick = tick;
    }
    clockOriginTick = tick;
  }

  /** The game is over; what the entities hold afterwards is not play. */
  void stop() {
    stopped = true;
  }

  void tick(long tick) {
    if (stopped || anchorTick < 0 || tick < nextGridTick) {
      return;
    }
    long grid = nextGridTick;
    sample(grid);
    nextGridTick = grid + STEP_TICKS * (1 + (tick - grid) / STEP_TICKS);
  }

  /**
   * The samples with game times taken from the tick where the clock actually started, so a pre-game
   * phase of another length shifts nothing.
   */
  SampleFacts result(long clockStartTick) {
    List<SampleFacts.PlayerSeries> all = new ArrayList<>();
    series.forEach((id, s) -> all.add(s.freeze(id)));
    int startSeconds = -(int) Math.round((clockStartTick - anchorTick) / (double) TICKS_PER_SECOND);
    List<SampleFacts.MinuteSnapshot> minutes =
        snapshots.stream()
            .map(
                raw ->
                    new SampleFacts.MinuteSnapshot(
                        raw.playerId(),
                        (int) Math.round((raw.gridTick() - clockStartTick) / (double) MINUTE_TICKS),
                        raw.values()))
            .filter(snapshot -> snapshot.gameMinute() >= -1)
            .toList();
    return new SampleFacts(startSeconds, STEP_SECONDS, all, minutes);
  }

  private void sample(long grid) {
    List<Controllers.View> players = Controllers.read(entities);
    boolean minuteBoundary = Math.floorMod(grid - clockOriginTick, MINUTE_TICKS) == 0;
    Entity resource = minuteBoundary ? first("CDOTA_PlayerResource") : null;
    Entity radiant = teamData(RADIANT_DATA);
    Entity dire = teamData(DIRE_DATA);
    int size = series.isEmpty() ? 0 : series.values().iterator().next().size();
    for (Controllers.View player : players) {
      Series s = series.computeIfAbsent(player.playerId(), id -> new Series());
      s.padTo(size);
      Entity teamData = player.team() == 2 ? radiant : dire;
      s.add(player.hero(), unspentGold(teamData, (player.playerId() / 2) % PLAYERS_PER_TEAM));
      if (minuteBoundary) {
        snapshots.add(
            new RawSnapshot(player.playerId(), grid, counters(player, resource, teamData)));
      }
    }
    int count = size + 1;
    series.values().forEach(s -> s.padTo(count));
    observers.forEach(observer -> observer.sampled(grid, players));
  }

  private long[] counters(Controllers.View player, Entity resource, Entity teamData) {
    int index = player.playerId() / 2;
    long[] values = new long[MinuteStat.values().length];
    for (MinuteStat stat : MinuteStat.values()) {
      boolean team = stat.source() == MinuteStat.Source.TEAM_DATA;
      values[stat.ordinal()] =
          read(team ? teamData : resource, stat, team ? index % PLAYERS_PER_TEAM : index);
    }
    return values;
  }

  private static long read(Entity source, MinuteStat stat, int position) {
    if (source == null) {
      return MinuteStat.MISSING;
    }
    String path =
        stat.source().arrayPrefix()
            + String.format(Locale.ROOT, "%04d.", position)
            + stat.property();
    return source.hasProperty(path) && source.getProperty(path) instanceof Number n
        ? n.longValue()
        : MinuteStat.MISSING;
  }

  private static String[] goldProperties(String property) {
    String[] names = new String[PLAYERS_PER_TEAM];
    for (int i = 0; i < names.length; i++) {
      names[i] = "m_vecDataTeam." + String.format(Locale.ROOT, "%04d.", i) + property;
    }
    return names;
  }

  private static int unspentGold(Entity teamData, int position) {
    if (teamData == null) {
      return MISSING;
    }
    String reliable = RELIABLE_GOLD[position];
    String unreliable = UNRELIABLE_GOLD[position];
    boolean hasReliable = teamData.getProperty(reliable) instanceof Number;
    boolean hasUnreliable = teamData.getProperty(unreliable) instanceof Number;
    if (!hasReliable && !hasUnreliable) {
      return MISSING;
    }
    long total =
        (hasReliable ? ((Number) teamData.getProperty(reliable)).longValue() : 0)
            + (hasUnreliable ? ((Number) teamData.getProperty(unreliable)).longValue() : 0);
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
  }

  /** The team data entities live for the whole game, so they are looked up until found. */
  private Entity teamData(String dtName) {
    return teamEntities.computeIfAbsent(dtName, this::first);
  }

  private Entity first(String dtName) {
    return entities.stream().filter(Entities.byDtName(dtName)).findFirst().orElse(null);
  }

  /** Counters as read at a sample tick, labelled with a game minute once the clock is known. */
  private record RawSnapshot(int playerId, long gridTick, long[] values) {}

  /** Growable columns of one player's samples. */
  private static final class Series {
    private int[] x = new int[256];
    private int[] y = new int[256];
    private int[] hp = new int[256];
    private int[] mana = new int[256];
    private int[] manaPoints = new int[256];
    private int[] attackRange = new int[256];
    private int[] level = new int[256];
    private int[] flags = new int[256];
    private int[] gold = new int[256];
    private int[] maxHp = new int[256];
    private boolean goldSeen;
    private boolean maxHpSeen;
    private int size;

    int size() {
      return size;
    }

    /** A player who has no hero yet, or joined late, keeps empty samples aligned with the rest. */
    void padTo(int target) {
      while (size < target) {
        add(null, MISSING);
      }
    }

    void add(Entity hero, int unspentGold) {
      if (size == x.length) {
        int capacity = size * 2;
        x = Arrays.copyOf(x, capacity);
        y = Arrays.copyOf(y, capacity);
        hp = Arrays.copyOf(hp, capacity);
        mana = Arrays.copyOf(mana, capacity);
        manaPoints = Arrays.copyOf(manaPoints, capacity);
        attackRange = Arrays.copyOf(attackRange, capacity);
        level = Arrays.copyOf(level, capacity);
        flags = Arrays.copyOf(flags, capacity);
        gold = Arrays.copyOf(gold, capacity);
        maxHp = Arrays.copyOf(maxHp, capacity);
      }
      if (unspentGold >= 0) {
        gold[size] = unspentGold;
        goldSeen = true;
      } else {
        gold[size] = 0;
      }
      maxHp[size] = 0;
      if (hero != null) {
        x[size] = Positions.x(hero);
        y[size] = Positions.y(hero);
        hp[size] = percent(hero, "m_iHealth", "m_iMaxHealth");
        mana[size] = percent(hero, "m_flMana", "m_flMaxMana");
        manaPoints[size] = (int) Math.round(number(hero, "m_flMana"));
        attackRange[size] = (int) number(hero, "m_iAttackRange");
        level[size] = (int) number(hero, "m_iCurrentLevel");
        flags[size] = number(hero, "m_lifeState") == 0 ? 1 : 0;
        if (hero.hasProperty("m_iMaxHealth")
            && hero.getProperty("m_iMaxHealth") instanceof Number n) {
          maxHp[size] = n.intValue();
          maxHpSeen = true;
        }
      }
      size++;
    }

    SampleFacts.PlayerSeries freeze(int playerId) {
      return new SampleFacts.PlayerSeries(
          playerId,
          Arrays.copyOf(x, size),
          Arrays.copyOf(y, size),
          Arrays.copyOf(hp, size),
          Arrays.copyOf(mana, size),
          Arrays.copyOf(manaPoints, size),
          Arrays.copyOf(attackRange, size),
          Arrays.copyOf(level, size),
          Arrays.copyOf(flags, size),
          goldSeen ? Arrays.copyOf(gold, size) : null,
          maxHpSeen ? Arrays.copyOf(maxHp, size) : null);
    }

    private static int percent(Entity hero, String value, String max) {
      double total = number(hero, max);
      return total <= 0 ? 0 : (int) Math.round(100 * number(hero, value) / total);
    }

    private static double number(Entity hero, String property) {
      return hero.hasProperty(property) && hero.getProperty(property) instanceof Number n
          ? n.doubleValue()
          : 0;
    }
  }
}
