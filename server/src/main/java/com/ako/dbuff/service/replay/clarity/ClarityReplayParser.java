package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.MapFacts;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayParser;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.MappedFileSource;

/**
 * Reads the replay with Clarity. It stops as soon as the game clock has started, which is all the
 * foundation needs; later extractors extend what this pass collects.
 */
@Slf4j
@Component
public class ClarityReplayParser implements ReplayParser {
  static final String CLARITY_VERSION = "5.0.1";

  private static final Map<Long, Supplier<DraftReader>> DRAFT_READERS =
      Map.of(AbilityDraftProfile.MODE_ID, AbilityDraftReader::new);

  @Override
  public String version() {
    return CLARITY_VERSION;
  }

  @Override
  public ReplayFacts parse(Path demoFile, GameModeProfile mode, BooleanSupplier cancelled) {
    DraftReader draftReader =
        DRAFT_READERS.getOrDefault(mode.gameModeId(), NoDraftReader::new).get();
    Collector collector = new Collector(cancelled, draftReader);
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
        collector.cutShort = true;
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
  @UsesStringTable("EntityNames")
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
    private static final String STATE = "m_pGameRules.m_nGameState";
    private static final String START_TIME = "m_pGameRules.m_flGameStartTime";
    private static final String PAUSED = "m_pGameRules.m_bGamePaused";

    @Insert Entities entities;
    @Insert StringTables stringTables;

    private final BooleanSupplier cancelled;
    ReplayFacts facts;
    boolean cutShort;
    private SampleRecorder recorder;
    private final CombatLogRecorder combatLog = new CombatLogRecorder();
    private MapFacts map;
    private StateRecorder stateRecorder;
    private final DraftReader draftReader;
    private long ticksSeen;
    private long gameOverTick = -1;
    private final GameClock clock = new GameClock();

    Collector(BooleanSupplier cancelled, DraftReader draftReader) {
      this.cancelled = cancelled;
      this.draftReader = draftReader;
    }

    @OnTickStart
    public void onTick(Context ctx, boolean synthetic) {
      recorder().tick(clock.gameTick(ctx.getTick()));
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
      draftReader.observe(rules, paths, count, (int) clock.gameTick(ctx.getTick()));
      if (rules.hasProperty(PAUSED) && rules.getProperty(PAUSED) instanceof Boolean paused) {
        clock.paused(paused, ctx.getTick());
      }
      if (!rules.hasProperty(STATE)) {
        return;
      }
      int state = rules.getProperty(STATE);
      if (state == PRE_GAME) {
        recorder().preGameStarted(clock.gameTick(ctx.getTick()));
      } else if (state == POST_GAME || state == POST_GAME_SCORES) {
        recorder().stop();
        if (facts != null && gameOverTick < 0) {
          gameOverTick = ctx.getTick();
        }
      } else if (state == GAME_IN_PROGRESS && facts == null) {
        if (!rules.hasProperty(START_TIME) || !(rules.getProperty(START_TIME) instanceof Number)) {
          return;
        }
        Number startTime = rules.getProperty(START_TIME);
        facts =
            ReplayFacts.builder()
                .clockStartTick((int) clock.gameTick(ctx.getTick()))
                .gameStartTime(startTime.doubleValue())
                .players(readPlayers())
                .draft(draftReader.read(rules))
                .build();
        recorder().gameStarted(clock.gameTick(ctx.getTick()));
        map = MapReader.read(entities, stringTables.forName("EntityNames"), rules);
      }
    }

    private StateRecorder stateRecorder() {
      recorder();
      return stateRecorder;
    }

    private SampleRecorder recorder() {
      if (recorder == null) {
        recorder = new SampleRecorder(entities);
        stateRecorder = new StateRecorder(entities, stringTables);
        recorder.observe(stateRecorder);
      }
      return recorder;
    }

    /** The facts with the samples of the whole game, once the replay has been read to its end. */
    ReplayFacts result() {
      return ReplayFacts.builder()
          .clockStartTick(facts.clockStartTick())
          .gameStartTime(facts.gameStartTime())
          .players(facts.players())
          .draft(facts.draft())
          .samples(recorder().result(facts.clockStartTick()))
          .combatLog(combatLog.result(facts.gameStartTime()))
          .map(map)
          .stateChanges(stateRecorder().result(facts.clockStartTick()))
          .cutShort(cutShort)
          .build();
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
