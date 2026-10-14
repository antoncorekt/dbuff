package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the swings of a game, the minutes in which the net worth difference between the teams moved
 * far and fast, and gathers the evidence for each: which sources of gold moved, and the fights,
 * buildings and buybacks inside it, and the item completed before it that most plausibly moved the
 * favoured team's strength (see {@link KeyItemFinder}). The primary cause is the source that
 * explains most.
 */
@Slf4j
@Order(60)
@Component
@RequiredArgsConstructor
public class SpikeAnalyzer implements ReplayAnalyzer {
  private static final long FIRST_DIRE_SLOT = 128;
  private static final int FIGHT_LEAD_SECONDS = 30;

  private final PlayerTimelineRepo timelineRepo;
  private final ReplayPlayerMinuteRepo minuteRepo;
  private final FightRepo fightRepo;
  private final BuildingKillContextRepo buildingRepo;
  private final BuybackEventRepo buybackRepo;
  private final GameSpikeRepo spikeRepo;
  private final GameSpikeFactorRepo factorRepo;
  private final ReplayAnalysisProperties properties;
  private final ReplayStateChangeRepo stateRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final KeyItemFinder keyItems;

  /** The gold sources told apart, each with the counters of a minute row that make it up. */
  private enum Source {
    HERO_KILLS(m -> sum(m.getHeroKillGold())),
    FARM(m -> sum(m.getCreepKillGold(), m.getNeutralKillGold(), m.getCreepDenyGold())),
    BUILDINGS(m -> sum(m.getBuildingGold())),
    ROSHAN(m -> sum(m.getRoshanGold())),
    BOUNTY(m -> sum(m.getBountyGold())),
    COMEBACK(m -> sum(m.getComebackGold())),
    /** Spent, so what the other team spent counts for the favoured team. */
    BUYBACKS(m -> -sum(m.getGoldSpentOnBuybacks()));

    private final Function<ReplayPlayerMinuteDomain, Long> counter;

    Source(Function<ReplayPlayerMinuteDomain, Long> counter) {
      this.counter = counter;
    }

    private static long sum(Long... values) {
      long total = 0;
      for (Long v : values) {
        total += v == null ? 0 : v;
      }
      return total;
    }
  }

  @Override
  public String name() {
    return "spikes";
  }

  @Override
  public Set<String> requires() {
    return Set.of("minutes");
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    TeamNetWorth netWorth = TeamNetWorth.of(timelineRepo.findByMatchId(matchId));
    if (netWorth == null) {
      log.info("Match {} has no complete net worth timeline, so no spikes", matchId);
      return;
    }
    double[] difference = new double[netWorth.radiant().length];
    double[] total = new double[difference.length];
    for (int i = 0; i < difference.length; i++) {
      difference[i] = netWorth.radiant()[i] - netWorth.dire()[i];
      total[i] = netWorth.radiant()[i] + netWorth.dire()[i];
    }
    List<SpikeDetector.Spike> spikes =
        new SpikeDetector(properties.spikeTuning()).detect(difference, total);
    Evidence evidence =
        new Evidence(
            minuteRepo.findByMatchId(matchId),
            fightRepo.findByMatchId(matchId),
            buildingRepo.findByMatchId(matchId),
            buybackRepo.findByMatchId(matchId),
            keyItems.completions(stateRepo.findByMatchIdOrderByGameSeconds(matchId)),
            activeSlotsByFight(fightPlayerRepo.findByMatchId(matchId)));
    List<GameSpikeDomain> rows = new ArrayList<>();
    List<GameSpikeFactorDomain> factors = new ArrayList<>();
    for (int index = 0; index < spikes.size(); index++) {
      SpikeDetector.Spike spike = spikes.get(index);
      String team = spike.delta() > 0 ? "RADIANT" : "DIRE";
      double sign = spike.delta() > 0 ? 1 : -1;
      List<GameSpikeFactorDomain> found = evidence.factors(matchId, index, spike, team);
      Primary primary =
          evidence.primary(
              found,
              team,
              properties.getSpikeMinSharePercent(),
              spike.startMinute() * 60,
              spike.endMinute() * 60);
      rows.add(
          GameSpikeDomain.builder()
              .matchId(matchId)
              .spikeIndex(index)
              .startSeconds(spike.startMinute() * 60)
              .endSeconds(spike.endMinute() * 60)
              .benefitingTeam(team)
              .deltaGold(Math.round(Math.abs(spike.delta())))
              .deltaPercent(
                  Math.round(Math.abs(spike.delta()) / total[spike.endMinute()] * 1000) / 10.0)
              .directionChange(spike.directionChange())
              .leadBefore(Math.round(sign * spike.leadBefore()))
              .leadAfter(Math.round(sign * spike.leadAfter()))
              .primaryCause(primary.cause().name())
              .confidence(primary.confidence())
              .analysisVersion(version())
              .build());
      factors.addAll(found);
    }
    spikeRepo.saveAll(rows);
    factorRepo.saveAll(factors);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    spikeRepo.deleteByMatchId(matchId);
    factorRepo.deleteByMatchId(matchId);
  }

  private static Map<Integer, Set<Long>> activeSlotsByFight(List<FightPlayerDomain> players) {
    Map<Integer, Set<Long>> active = new LinkedHashMap<>();
    for (FightPlayerDomain p : players) {
      if (Presence.ACTIVE.name().equals(p.getPresence()) && p.getPlayerSlot() != null) {
        active.computeIfAbsent(p.getFightIndex(), k -> new HashSet<>()).add(p.getPlayerSlot());
      }
    }
    return active;
  }

  /** The cause named for a swing and how much of the swing its source explains, 0 to 1. */
  private record Primary(SpikeCause cause, double confidence) {}

  /** What the stored tables say happened in a swing's minutes. */
  private final class Evidence {
    private final Map<Long, List<ReplayPlayerMinuteDomain>> minutes = new LinkedHashMap<>();
    private final List<FightDomain> fights;
    private final List<BuildingKillContextDomain> buildings;
    private final List<BuybackEventDomain> buybacks;
    private final List<KeyItemFinder.Completion> completions;
    private final Map<Integer, Set<Long>> activeSlots;

    Evidence(
        List<ReplayPlayerMinuteDomain> minuteRows,
        List<FightDomain> fights,
        List<BuildingKillContextDomain> buildings,
        List<BuybackEventDomain> buybacks,
        List<KeyItemFinder.Completion> completions,
        Map<Integer, Set<Long>> activeSlots) {
      minuteRows.forEach(
          m -> minutes.computeIfAbsent(m.getPlayerSlot(), k -> new ArrayList<>()).add(m));
      this.fights = fights;
      this.buildings = buildings;
      this.buybacks = buybacks;
      this.completions = completions;
      this.activeSlots = activeSlots;
    }

    List<GameSpikeFactorDomain> factors(
        long matchId, int index, SpikeDetector.Spike spike, String team) {
      List<GameSpikeFactorDomain> factors = new ArrayList<>();
      double swing = Math.abs(spike.delta());
      int from = spike.startMinute() * 60;
      int to = spike.endMinute() * 60;
      for (Source source : Source.values()) {
        long gain = gain(source, team, spike.startMinute(), spike.endMinute());
        if (gain != 0) {
          factors.add(
              factor(matchId, index, "SOURCE", source.name(), gain, 100.0 * gain / swing, null));
        }
      }
      List<KeyItemFinder.FightActivity> swingFights = new ArrayList<>();
      for (FightDomain fight : fights) {
        if (fight.getStartSeconds() >= from - FIGHT_LEAD_SECONDS && fight.getStartSeconds() <= to) {
          swingFights.add(
              new KeyItemFinder.FightActivity(
                  fight.getStartSeconds(),
                  activeSlots.getOrDefault(fight.getFightIndex(), Set.of())));
          factors.add(
              factor(
                  matchId,
                  index,
                  "FIGHT",
                  String.valueOf(fight.getFightIndex()),
                  null,
                  null,
                  "winner="
                      + fight.getWinner()
                      + " deaths="
                      + fight.getRadiantDeaths()
                      + ":"
                      + fight.getDireDeaths()));
        }
      }
      for (BuildingKillContextDomain building : buildings) {
        if (building.getSeconds() >= from
            && building.getSeconds() <= to
            && !building.getOwnerTeam().equals(team)) {
          factors.add(
              factor(
                  matchId,
                  index,
                  "OBJECTIVE",
                  building.getBuildingName(),
                  null,
                  null,
                  building.getKind()));
        }
      }
      for (BuybackEventDomain buyback : buybacks) {
        if (buyback.getSeconds() >= from
            && buyback.getSeconds() <= to
            && !teamOf(buyback.getPlayerSlot()).equals(team)) {
          factors.add(
              factor(
                  matchId,
                  index,
                  "BUYBACK",
                  String.valueOf(buyback.getPlayerSlot()),
                  null,
                  null,
                  null));
        }
      }
      keyItems
          .find(completions, new KeyItemFinder.Swing(from, TeamSide.valueOf(team), swingFights))
          .ifPresent(item -> factors.add(itemFactor(matchId, index, item)));
      return factors;
    }

    /** The source that explains the most, if it explains at least the minimum share. */
    Primary primary(
        List<GameSpikeFactorDomain> factors, String team, int minSharePercent, int from, int to) {
      return factors.stream()
          .filter(
              f ->
                  "SOURCE".equals(f.getFactorType())
                      && f.getSharePercent() != null
                      && f.getSharePercent() >= minSharePercent)
          .max(Comparator.comparingDouble(GameSpikeFactorDomain::getSharePercent))
          .map(
              f ->
                  new Primary(
                      causeOf(Source.valueOf(f.getRef()), team, from, to),
                      Math.min(1, Math.round(f.getSharePercent()) / 100.0)))
          .orElse(new Primary(SpikeCause.OTHER, 0));
    }

    private SpikeCause causeOf(Source source, String team, int from, int to) {
      return switch (source) {
        case HERO_KILLS -> fightWonBy(team, from, to) ? SpikeCause.FIGHT_WIN : SpikeCause.PICKOFF;
        case FARM, BOUNTY -> SpikeCause.CARRY_FARM;
        case BUILDINGS -> SpikeCause.OBJECTIVE;
        case ROSHAN -> SpikeCause.ROSHAN_AEGIS;
        case COMEBACK -> SpikeCause.COMEBACK_GOLD;
        case BUYBACKS -> SpikeCause.BUYBACK_SWING;
      };
    }

    /** Whether the team won a fight that began in the window, or just before it. */
    private boolean fightWonBy(String team, int from, int to) {
      return fights.stream()
          .anyMatch(
              f ->
                  team.equals(f.getWinner())
                      && f.getStartSeconds() >= from - FIGHT_LEAD_SECONDS
                      && f.getStartSeconds() <= to);
    }

    /** What the team gained from a source between two minutes, less what the other team did. */
    private long gain(Source source, String team, int fromMinute, int toMinute) {
      long gain = 0;
      for (Map.Entry<Long, List<ReplayPlayerMinuteDomain>> e : minutes.entrySet()) {
        long change = at(e.getValue(), source, toMinute) - at(e.getValue(), source, fromMinute);
        gain += teamOf(e.getKey()).equals(team) ? change : -change;
      }
      return gain;
    }

    private long at(List<ReplayPlayerMinuteDomain> rows, Source source, int minute) {
      return rows.stream()
          .filter(r -> r.getGameMinute() <= minute)
          .max(Comparator.comparingInt(ReplayPlayerMinuteDomain::getGameMinute))
          .map(source.counter)
          .orElse(0L);
    }
  }

  private static String teamOf(long slot) {
    return slot >= FIRST_DIRE_SLOT ? "DIRE" : "RADIANT";
  }

  private GameSpikeFactorDomain itemFactor(long matchId, int index, KeyItemFinder.Completion item) {
    GameSpikeFactorDomain row =
        factor(
            matchId,
            index,
            "ITEM",
            item.item(),
            item.cost(),
            null,
            String.valueOf(Math.round(item.seconds())));
    row.setPlayerSlot(item.playerSlot());
    return row;
  }

  private GameSpikeFactorDomain factor(
      long matchId, int index, String type, String ref, Long gold, Double share, String detail) {
    return GameSpikeFactorDomain.builder()
        .matchId(matchId)
        .spikeIndex(index)
        .factorType(type)
        .ref(ref)
        .goldValue(gold)
        .sharePercent(share == null ? null : Math.round(share * 10) / 10.0)
        .detail(detail)
        .analysisVersion(version())
        .build();
  }
}
