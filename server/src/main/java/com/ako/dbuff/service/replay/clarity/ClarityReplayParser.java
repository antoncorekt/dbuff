package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.service.replay.ReplayException;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.ReplayParser;
import com.ako.dbuff.service.replay.ReplayPlayerFact;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;
import skadistats.clarity.event.Insert;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

/**
 * Reads the replay with Clarity. It stops as soon as the game clock has started, which is all the
 * foundation needs; later extractors extend what this pass collects.
 */
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
    } catch (StopParsing stop) {
      // The collector has what it needs.
    } catch (CancelledParsing cancel) {
      throw new ReplayException(ReplayStatus.FAILED_TIMEOUT, "Parse stopped: timed out");
    } catch (Exception e) {
      throw new ReplayException(
          ReplayStatus.FAILED_PARSER, "Replay could not be parsed: " + e.getMessage(), e);
    }
    if (collector.facts == null) {
      throw new ReplayException(
          ReplayStatus.SKIPPED_NOT_PLAYED, "Replay never reached the playing phase");
    }
    return collector.facts;
  }

  private static final class StopParsing extends RuntimeException {
    StopParsing() {
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
    private static final int GAME_IN_PROGRESS = 5;
    private static final int RADIANT = 2;
    private static final int DIRE = 3;
    private static final int CANCEL_CHECK_TICKS = 300;
    private static final String STATE = "m_pGameRules.m_nGameState";
    private static final String START_TIME = "m_pGameRules.m_flGameStartTime";

    @Insert Entities entities;

    private final BooleanSupplier cancelled;
    ReplayFacts facts;

    Collector(BooleanSupplier cancelled) {
      this.cancelled = cancelled;
    }

    @OnTickStart
    public void onTick(Context ctx, boolean synthetic) {
      if (ctx.getTick() % CANCEL_CHECK_TICKS == 0 && cancelled.getAsBoolean()) {
        throw new CancelledParsing();
      }
    }

    @OnEntityUpdated(classPattern = "CDOTAGamerulesProxy")
    public void rulesUpdated(Context ctx, Entity rules, FieldPath[] paths, int count) {
      if (facts != null || !rules.hasProperty(STATE)) {
        return;
      }
      int state = rules.getProperty(STATE);
      if (state != GAME_IN_PROGRESS) {
        return;
      }
      Number startTime = rules.getProperty(START_TIME);
      facts = new ReplayFacts(ctx.getTick(), startTime.doubleValue(), readPlayers());
      throw new StopParsing();
    }

    private List<ReplayPlayerFact> readPlayers() {
      List<ReplayPlayerFact> players = new ArrayList<>();
      entities.stream()
          .filter(Entities.byDtName("CDOTAPlayerController"))
          .forEach(
              controller -> {
                int playerId = controller.getProperty("m_nPlayerID");
                if (playerId < 0) {
                  return;
                }
                int team = controller.getProperty("m_iTeamNum");
                if (team != RADIANT && team != DIRE) {
                  return;
                }
                long steamId = ((Number) controller.getProperty("m_steamID")).longValue();
                int heroHandle = controller.getProperty("m_hAssignedHero");
                Entity hero = entities.getByHandle(heroHandle);
                String heroName = hero == null ? null : heroName(hero);
                players.add(new ReplayPlayerFact(playerId, team, steamId, heroName));
              });
      players.sort((a, b) -> Integer.compare(a.replayPlayerId(), b.replayPlayerId()));
      return players;
    }

    private static String heroName(Entity hero) {
      String dt = hero.getDtClass().getDtName();
      return dt.startsWith("CDOTA_Unit_Hero_") ? dt.substring("CDOTA_Unit_Hero_".length()) : dt;
    }
  }
}
