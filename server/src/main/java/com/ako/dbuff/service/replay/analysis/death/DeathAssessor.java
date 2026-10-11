package com.ako.dbuff.service.replay.analysis.death;

import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.fight.FightAssessor.Trace;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Says what was going on around one hero death: where it happened, who did it, what the hero had to
 * survive it, and the first thing that explains it.
 */
public final class DeathAssessor {
  private static final String BKB = "item_black_king_bar";
  private static final String BKB_IMMUNE = "modifier_black_king_bar_immune";
  private static final String TELEPORT_SCROLL = "item_tpscroll";
  private static final Set<String> SAVE_ITEMS =
      Set.of(
          "item_glimmer_cape",
          "item_force_staff",
          "item_cyclone",
          "item_ghost",
          "item_lotus_orb",
          "item_aeon_disk",
          "item_blink",
          "item_swift_blink",
          "item_overwhelming_blink",
          "item_arcane_blink");
  private static final Set<String> DISABLES =
      Set.of(
          "modifier_stunned",
          "modifier_silence",
          "modifier_silenced",
          "modifier_hexxed",
          "modifier_rooted",
          "modifier_sleep",
          "modifier_fear",
          "modifier_taunt");
  private static final int EXPIRY_WINDOW_SECONDS = 3;
  private static final int CENTISECONDS = 100;
  private static final int DEFAULT_IMMUNITY_SECONDS = 10;
  private static final int TOP_SOURCES = 3;

  /**
   * @param lookbackSeconds how far back damage, disables and BKB use are looked at
   * @param burstDamage damage in the last three seconds from which a death is a burst
   * @param isolationRadius a hero with no ally and two enemies this close was caught out
   * @param disableSeconds disabled this long in the lookback is a disable chain
   * @param reincarnationSeconds alive this long after dying means Aegis or reincarnation
   * @param outOfPositionRanks standing this many places further forward than his attack range
   *     suggests, in the fight he died in, is being out of position
   */
  public record Tuning(
      int lookbackSeconds,
      int burstDamage,
      int isolationRadius,
      int disableSeconds,
      int reincarnationSeconds,
      int outOfPositionRanks) {}

  /** A hero death from the combat log. {@code killerFlags} are the CombatEvent flag bits. */
  public record Death(double second, int victim, String killer, int killerFlags, int assistMask) {}

  /**
   * @param x null when the hero had no position
   */
  public record Assessment(
      KillerKind killerKind,
      Integer x,
      Integer y,
      Zone zone,
      boolean isolated,
      boolean reincarnated,
      boolean bkbOwned,
      boolean bkbReady,
      boolean bkbActive,
      boolean bkbExpiredJustBefore,
      double disableSeconds,
      long damageLookback,
      long damageLastThreeSeconds,
      String topSources,
      String saveItemsOwned,
      boolean teleportReady,
      DeathReason reason,
      Integer frontlineDelta) {}

  private static final Pattern BUILDING_NAME =
      Pattern.compile("^npc_dota_(goodguys|badguys)_(tower|fort|.*rax)");

  private final Tuning tuning;

  public DeathAssessor(Tuning tuning) {
    this.tuning = tuning;
  }

  /**
   * @param victimEvents the combat-log events whose target is the dying hero (damage and modifiers)
   * @param layout may be null, then the zone is null
   */
  public Assessment assess(
      Death death,
      Map<Integer, Trace> traces,
      HeroStateTimeline state,
      List<CombatEvent> victimEvents,
      MapLayout layout) {
    return assess(death, traces, state, victimEvents, layout, null);
  }

  /**
   * @param frontlineDelta how far forward the hero stood in the fight he died in compared with his
   *     attack range, as {@link FrontlineRanker} says; null when he died outside a fight
   */
  public Assessment assess(
      Death death,
      Map<Integer, Trace> traces,
      HeroStateTimeline state,
      List<CombatEvent> victimEvents,
      MapLayout layout,
      Integer frontlineDelta) {
    double from = death.second() - tuning.lookbackSeconds();
    KillerKind kind = kind(death);
    int[] position = position(traces.get(death.victim()), death.second());
    Zone zone = layout == null || position == null ? null : layout.zone(position[0], position[1]);
    boolean reincarnated = aliveLater(traces.get(death.victim()), death.second());
    List<double[]> immunity = immunityWindows(victimEvents);
    boolean owned = state.owns(BKB, death.second());
    boolean ownedThroughout = owned && state.owns(BKB, from);
    boolean ready = ownedThroughout && state.ready(BKB, from);
    boolean active =
        immunity.stream().anyMatch(w -> death.second() >= w[0] && death.second() <= w[1]);
    boolean expired =
        !active
            && immunity.stream()
                .anyMatch(
                    w -> w[1] >= death.second() - EXPIRY_WINDOW_SECONDS && w[1] < death.second());
    boolean usedInLookback =
        immunity.stream().anyMatch(w -> w[1] >= from && w[0] <= death.second());
    double disabled = disabledSeconds(victimEvents, from, death.second());
    List<CombatEvent> hits = damage(victimEvents, from, death.second());
    long lookbackDamage = hits.stream().mapToLong(CombatEvent::value).sum();
    long lastThree =
        hits.stream()
            .filter(e -> e.gameSecond() >= death.second() - EXPIRY_WINDOW_SECONDS)
            .mapToLong(CombatEvent::value)
            .sum();
    boolean isolated = isolated(death, traces);
    List<String> saves =
        SAVE_ITEMS.stream().filter(i -> state.owns(i, death.second())).sorted().toList();
    boolean teleport =
        state.owns(TELEPORT_SCROLL, death.second()) && state.ready(TELEPORT_SCROLL, death.second());
    DeathReason reason =
        reason(
            kind,
            reincarnated,
            owned,
            ownedThroughout,
            ready,
            active,
            expired,
            usedInLookback,
            disabled,
            isolated,
            frontlineDelta != null && frontlineDelta <= -tuning.outOfPositionRanks(),
            lastThree);
    return new Assessment(
        kind,
        position == null ? null : position[0],
        position == null ? null : position[1],
        zone,
        isolated,
        reincarnated,
        owned,
        ready,
        active,
        expired,
        disabled,
        lookbackDamage,
        lastThree,
        topSources(hits),
        String.join(",", saves),
        teleport,
        reason,
        frontlineDelta);
  }

  private DeathReason reason(
      KillerKind kind,
      boolean reincarnated,
      boolean owned,
      boolean ownedThroughout,
      boolean ready,
      boolean active,
      boolean expired,
      boolean usedInLookback,
      double disabled,
      boolean isolated,
      boolean outOfPosition,
      long lastThree) {
    if (reincarnated) {
      return DeathReason.REINCARNATED;
    }
    if (active) {
      return DeathReason.DIED_IMMUNE;
    }
    if (expired) {
      return DeathReason.BKB_EXPIRED;
    }
    if (kind == KillerKind.HERO && ready && !usedInLookback) {
      return DeathReason.BKB_READY_UNUSED;
    }
    if (kind == KillerKind.HERO && ownedThroughout && !ready) {
      return DeathReason.BKB_ON_COOLDOWN;
    }
    if (disabled >= tuning.disableSeconds()) {
      return DeathReason.DISABLE_CHAIN;
    }
    if (isolated) {
      return DeathReason.CAUGHT_OUT;
    }
    if (outOfPosition) {
      return DeathReason.OUT_OF_POSITION;
    }
    if (kind == KillerKind.TOWER) {
      return DeathReason.TOWER;
    }
    if (lastThree >= tuning.burstDamage()) {
      return DeathReason.BURST;
    }
    return !owned && kind == KillerKind.HERO ? DeathReason.NO_BKB : DeathReason.OTHER;
  }

  private static KillerKind kind(Death death) {
    if ((death.killerFlags() & CombatEvent.ATTACKER_HERO) != 0
        && (death.killerFlags() & CombatEvent.ATTACKER_ILLUSION) == 0) {
      return KillerKind.HERO;
    }
    String killer = death.killer();
    if (killer.startsWith("npc_dota_neutral_")) {
      return KillerKind.NEUTRAL;
    }
    if (BUILDING_NAME.matcher(killer).find()) {
      return KillerKind.TOWER;
    }
    if (killer.startsWith("npc_dota_creep_")) {
      return KillerKind.CREEP;
    }
    return KillerKind.OTHER;
  }

  private static int[] position(Trace trace, double second) {
    if (trace == null || trace.x().length == 0) {
      return null;
    }
    int i = trace.indexAt(second);
    return trace.level()[i] == 0 ? null : new int[] {trace.x()[i], trace.y()[i]};
  }

  /** A hero still or again alive a few seconds after dying was brought back, not respawned. */
  private boolean aliveLater(Trace trace, double second) {
    if (trace == null || trace.x().length == 0) {
      return false;
    }
    int i = trace.indexAt(second + tuning.reincarnationSeconds());
    return trace.level()[i] > 0 && (trace.flags()[i] & 1) == 1;
  }

  private boolean isolated(Death death, Map<Integer, Trace> traces) {
    int[] me = position(traces.get(death.victim()), death.second());
    if (me == null) {
      return false;
    }
    int allies = 0;
    int enemies = 0;
    for (Map.Entry<Integer, Trace> other : traces.entrySet()) {
      if (other.getKey() == death.victim() || other.getValue().x().length == 0) {
        continue;
      }
      Trace t = other.getValue();
      int i = t.indexAt(death.second());
      boolean alive = t.level()[i] > 0 && (t.flags()[i] & 1) == 1;
      if (alive && Math.hypot(t.x()[i] - me[0], t.y()[i] - me[1]) <= tuning.isolationRadius()) {
        if (FightDetector.team(other.getKey()) == FightDetector.team(death.victim())) {
          allies++;
        } else {
          enemies++;
        }
      }
    }
    return allies == 0 && enemies >= 2;
  }

  /** From each immunity modifier added on the hero to the one removed after it. */
  private static List<double[]> immunityWindows(List<CombatEvent> events) {
    List<double[]> windows = new ArrayList<>();
    double start = Double.NaN;
    for (CombatEvent e : events) {
      if (!BKB_IMMUNE.equals(e.inflictor())) {
        continue;
      }
      if (e.kind() == CombatEventKind.MODIFIER_ADD) {
        start = e.gameSecond();
        double length = e.aux2() > 0 ? e.aux2() / (double) CENTISECONDS : DEFAULT_IMMUNITY_SECONDS;
        windows.add(new double[] {start, start + length});
      } else if (e.kind() == CombatEventKind.MODIFIER_REMOVE
          && !windows.isEmpty()
          && !Double.isNaN(start)) {
        windows.getLast()[1] = e.gameSecond();
        start = Double.NaN;
      }
    }
    return windows;
  }

  /** The time the hero spent disabled in {@code [from, to]}, overlapping disables counted once. */
  private static double disabledSeconds(List<CombatEvent> events, double from, double to) {
    List<double[]> intervals = new ArrayList<>();
    for (CombatEvent e : events) {
      if (e.kind() != CombatEventKind.MODIFIER_ADD) {
        continue;
      }
      double length = 0;
      if (e.aux() > 0) {
        length = e.aux() / (double) CENTISECONDS;
      } else if (DISABLES.contains(e.inflictor()) && e.aux2() > 0) {
        length = e.aux2() / (double) CENTISECONDS;
      }
      if (length > 0 && e.gameSecond() <= to && e.gameSecond() + length >= from) {
        intervals.add(
            new double[] {Math.max(from, e.gameSecond()), Math.min(to, e.gameSecond() + length)});
      }
    }
    intervals.sort(Comparator.comparingDouble(i -> i[0]));
    double total = 0;
    double end = Double.NEGATIVE_INFINITY;
    for (double[] i : intervals) {
      double start = Math.max(i[0], end);
      if (i[1] > start) {
        total += i[1] - start;
      }
      end = Math.max(end, i[1]);
    }
    return total;
  }

  private static List<CombatEvent> damage(List<CombatEvent> events, double from, double to) {
    return events.stream()
        .filter(
            e ->
                e.kind() == CombatEventKind.DAMAGE
                    && e.gameSecond() >= from
                    && e.gameSecond() <= to)
        .toList();
  }

  /** The biggest damage dealers in the lookback, as {@code name:damage}. */
  private static String topSources(List<CombatEvent> hits) {
    Map<String, Long> bySource = new TreeMap<>();
    for (CombatEvent e : hits) {
      String source = e.attacker().isEmpty() ? "unknown" : e.attacker();
      bySource.merge(source, (long) e.value(), Long::sum);
    }
    return bySource.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(TOP_SOURCES)
        .map(e -> e.getKey() + ":" + e.getValue())
        .collect(Collectors.joining(","));
  }
}
