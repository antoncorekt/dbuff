package com.ako.dbuff.service.replay.analysis.buyback;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.replay.Buybacks;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fight.FightDetector;
import com.ako.dbuff.service.replay.analysis.fight.FightLookup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lists the buybacks of a match with the death each followed and how that fight went. */
@Order(35)
@Component
@RequiredArgsConstructor
public class BuybackAnalyzer implements ReplayAnalyzer {
  private final ReplayPlayerRepo playerRepo;
  private final FightRepo fightRepo;
  private final BuybackEventRepo buybackRepo;
  private final CombatLogReader combatLog;

  @Override
  public String name() {
    return "buybacks";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "combat-log");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<CombatEvent> events = combatLog.events(matchId);
    List<Buybacks.Buyback> buybacks = Buybacks.of(events);
    if (buybacks.isEmpty()) {
      return;
    }
    Map<String, Integer> playerOf =
        CombatHeroes.playerIndexByHero(events, playerRepo.findByMatchId(matchId));
    Map<Integer, List<Double>> deaths = CombatHeroes.deathTimes(events, playerOf);
    List<FightDomain> fights = fightRepo.findByMatchId(matchId);
    buybackRepo.saveAll(
        buybacks.stream().map(b -> row(matchId, b, deaths.get(b.player()), fights)).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    buybackRepo.deleteByMatchId(matchId);
  }

  private BuybackEventDomain row(
      long matchId, Buybacks.Buyback buyback, List<Double> deaths, List<FightDomain> fights) {
    Double death = lastDeathBefore(deaths, buyback.second());
    FightDomain fight = death == null ? null : FightLookup.at(fights, death);
    return BuybackEventDomain.builder()
        .matchId(matchId)
        .playerSlot(PlayerSlots.slot(buyback.player()))
        .seconds(buyback.second())
        .deathSeconds(death)
        .secondsDead(death == null ? null : buyback.second() - death)
        .fightIndex(fight == null ? null : fight.getFightIndex())
        .fightWon(fight == null ? null : won(fight, buyback.player()))
        .analysisVersion(version())
        .build();
  }

  /** The death the buyback paid for, if it came no longer after it than a respawn takes. */
  private static Double lastDeathBefore(List<Double> deaths, double buyback) {
    if (deaths == null) {
      return null;
    }
    return deaths.stream()
        .filter(d -> d <= buyback && buyback - d <= Buybacks.LONGEST_AFTER_DEATH_SECONDS)
        .max(Double::compare)
        .orElse(null);
  }

  private static Boolean won(FightDomain fight, int player) {
    String team = FightDetector.team(player) == 0 ? "RADIANT" : "DIRE";
    return switch (String.valueOf(fight.getWinner())) {
      case "RADIANT", "DIRE" -> fight.getWinner().equals(team);
      default -> null;
    };
  }
}
