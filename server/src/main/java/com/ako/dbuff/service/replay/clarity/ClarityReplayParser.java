package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.DraftFacts;
import com.ako.dbuff.service.replay.DraftPick;
import com.ako.dbuff.service.replay.DraftPoolEntry;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayParser;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import skadistats.clarity.event.Insert;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

/**
 * Reads the replay with Clarity. It stops as soon as the game clock has started, which is all the
 * foundation needs; later extractors extend what this pass collects.
 */
@Slf4j
@Component
public class ClarityReplayParser implements ReplayParser {
  static final String CLARITY_VERSION = "5.0.1";

  @Override
  public String version() {
    return CLARITY_VERSION;
  }

  @Override
  public ReplayFacts parse(Path demoFile, BooleanSupplier cancelled) {
    Collector collector = new Collector(cancelled);
    try {
      new SimpleRunner(new MappedFileSource(demoFile.toString())).runWith(collector);
    } catch (Exception e) {
      if (hasCause(e, CancelledParsing.class)) {
        throw new ReplayException(ReplayStatus.FAILED_TIMEOUT, "Parse stopped: timed out");
      }
      if (collector.facts == null) {
        throw new ReplayException(
            ReplayStatus.FAILED_PARSER, "Replay could not be parsed: " + e.getMessage(), e);
      }
      if (!hasCause(e, GameOver.class)) {
        log.warn("Replay of the game was cut short, keeping what was read: {}", e.getMessage());
      }
    }
    if (collector.facts == null) {
      throw new ReplayException(
          ReplayStatus.SKIPPED_NOT_PLAYED, "Replay never reached the playing phase");
    }
    return collector.result();
  }

  /** The runner may wrap what a handler throws, so the control exceptions are looked up. */
  private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  /** Thrown when the game ends, so that nothing after it is read. */
  private static final class GameOver extends RuntimeException {
    GameOver() {
      super(null, null, false, false);
    }
  }

  private static final class CancelledParsing extends RuntimeException {
    CancelledParsing() {
      super(null, null, false, false);
    }
  }

  @UsesEntities
  public static class Collector {
    private static final int PRE_GAME = 4;
    private static final int GAME_IN_PROGRESS = 5;

    /** The Ancient's fall and the last deaths are logged just after the state changes. */
    private static final int AFTER_GAME_TICKS = 300;

    private static final int POST_GAME = 6;
    private static final int POST_GAME_SCORES = 7;
    private static final int RADIANT = 2;
    private static final int DIRE = 3;
    private static final int CANCEL_CHECK_TICKS = 300;
    private static final int NOT_PICKED = 20;
    private static final String DRAFT = "m_pGameRules.m_AbilityDraftAbilities.";
    private static final Pattern PICK_FIELD =
        Pattern.compile("^m_pGameRules\\.m_AbilityDraftAbilities\\.(\\d+)\\.m_unPlayerID$");
    private static final String STATE = "m_pGameRules.m_nGameState";
    private static final String START_TIME = "m_pGameRules.m_flGameStartTime";

    @Insert Entities entities;

    private final BooleanSupplier cancelled;
    ReplayFacts facts;
    private SampleRecorder recorder;
    private final CombatLogRecorder combatLog = new CombatLogRecorder();
    private final List<DraftPick> picks = new ArrayList<>();
    private final BitSet picked = new BitSet();
    private long ticksSeen;
    private long gameOverTick = -1;

    Collector(BooleanSupplier cancelled) {
      this.cancelled = cancelled;
    }

    @OnTickStart
    public void onTick(Context ctx, boolean synthetic) {
      recorder().tick(ctx.getTick());
      if (gameOverTick >= 0 && ctx.getTick() - gameOverTick > AFTER_GAME_TICKS) {
        throw new GameOver();
      }
      if (++ticksSeen % CANCEL_CHECK_TICKS == 0 && cancelled.getAsBoolean()) {
        throw new CancelledParsing();
      }
    }

    @OnCombatLogEntry
    public void onCombatLog(CombatLogEntry entry) {
      combatLog.record(entry);
    }

    @OnEntityUpdated(classPattern = "CDOTAGamerulesProxy")
    public void rulesUpdated(Context ctx, Entity rules, FieldPath[] paths, int count) {
      recordPicks(ctx, rules, paths, count);
      if (!rules.hasProperty(STATE)) {
        return;
      }
      int state = rules.getProperty(STATE);
      if (state == PRE_GAME) {
        recorder().preGameStarted(ctx.getTick());
      } else if (state == POST_GAME || state == POST_GAME_SCORES) {
        recorder().stop();
        if (facts != null && gameOverTick < 0) {
          gameOverTick = ctx.getTick();
        }
      } else if (state == GAME_IN_PROGRESS && facts == null) {
        Number startTime = rules.getProperty(START_TIME);
        facts =
            new ReplayFacts(
                ctx.getTick(), startTime.doubleValue(), readPlayers(), readDraft(rules));
        recorder().gameStarted(ctx.getTick());
      }
    }

    private SampleRecorder recorder() {
      if (recorder == null) {
        recorder = new SampleRecorder(entities);
      }
      return recorder;
    }

    /** The facts with the samples of the whole game, once the replay has been read to its end. */
    ReplayFacts result() {
      return new ReplayFacts(
          facts.clockStartTick(),
          facts.gameStartTime(),
          facts.players(),
          facts.draft(),
          recorder().result(facts.clockStartTick()),
          combatLog.result(facts.gameStartTime()));
    }

    /** A pick is the moment an entry's player id changes from "nobody" to a player. */
    private void recordPicks(Context ctx, Entity rules, FieldPath[] paths, int count) {
      for (int i = 0; i < count; i++) {
        Matcher match = PICK_FIELD.matcher(rules.getNameForFieldPath(paths[i]));
        if (!match.matches()) {
          continue;
        }
        int poolIndex = Integer.parseInt(match.group(1));
        int player = ((Number) rules.getPropertyForFieldPath(paths[i])).intValue();
        if (player != NOT_PICKED && !picked.get(poolIndex)) {
          picked.set(poolIndex);
          picks.add(new DraftPick(picks.size() + 1, poolIndex, player, ctx.getTick()));
        }
      }
    }

    /** The final pool, read when the game starts so that moved skills show their final slot. */
    private DraftFacts readDraft(Entity rules) {
      List<DraftPoolEntry> pool = new ArrayList<>();
      for (int index = 0; ; index++) {
        String prefix = DRAFT + String.format("%04d", index) + ".";
        Number ability = number(rules, prefix + "m_nAbilityID");
        Number player = number(rules, prefix + "m_unPlayerID");
        Number slot = number(rules, prefix + "m_unAbilityPlayerSlot");
        if (ability == null || ability.intValue() == 0 || player == null || slot == null) {
          break;
        }
        pool.add(
            new DraftPoolEntry(
                index,
                ability.intValue() / 2,
                player.intValue() == NOT_PICKED ? -1 : player.intValue(),
                slot.intValue()));
      }
      return pool.isEmpty() ? null : new DraftFacts(pool, List.copyOf(picks));
    }

    /** The array has room past its last entry, where the property exists but holds nothing. */
    private static Number number(Entity rules, String property) {
      return rules.hasProperty(property) ? (Number) rules.getProperty(property) : null;
    }

    private List<ReplayPlayerFact> readPlayers() {
      return Controllers.read(entities).stream()
          .map(
              c ->
                  new ReplayPlayerFact(
                      c.playerId(),
                      c.team(),
                      c.steamId(),
                      c.hero() == null ? null : heroName(c.hero())))
          .toList();
    }

    private static String heroName(Entity hero) {
      String dt = hero.getDtClass().getDtName();
      return dt.startsWith("CDOTA_Unit_Hero_") ? dt.substring("CDOTA_Unit_Hero_".length()) : dt;
    }
  }
}
