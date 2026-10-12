package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one {@code fight_explanation} per fight with a decided winner, seen from the loser: the
 * swing that lists the fight, the gold its deaths cost, and how many reasons were stored for the
 * loser. A fight with no reason is kept with a count of zero, so a gap in the rules shows. Nothing
 * is capped; ranking is {@link ReasonRanking}, applied by the reader.
 */
@Order(70)
@Component
@RequiredArgsConstructor
public class FightLossAnalyzer implements ReplayAnalyzer {
  private static final String FIGHT_FACTOR = "FIGHT";

  private final FightRepo fightRepo;
  private final FightReasonRepo reasonRepo;
  private final GameSpikeRepo spikeRepo;
  private final GameSpikeFactorRepo factorRepo;
  private final DeathAnalysisRepo deathRepo;
  private final DeathEventRepo deathEventRepo;
  private final FightExplanationRepo explanationRepo;
  private final FightLossProperties properties;

  @Override
  public String name() {
    return "fight-loss";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Map<Integer, List<FightReasonDomain>> reasons =
        group(reasonRepo.findByMatchId(matchId), FightReasonDomain::getFightIndex);
    Map<Integer, List<DeathAnalysisDomain>> deaths =
        group(deathRepo.findByMatchId(matchId), DeathAnalysisDomain::getFightIndex);
    List<DeathEventDomain> deathEvents = deathEventRepo.findByMatchId(matchId);
    Map<Integer, GameSpikeDomain> spikes =
        spikeRepo.findByMatchId(matchId).stream()
            .filter(sp -> sp.getSpikeIndex() != null)
            .collect(
                Collectors.toMap(GameSpikeDomain::getSpikeIndex, Function.identity(), (a, b) -> a));
    List<GameSpikeFactorDomain> fightFactors =
        factorRepo.findByMatchId(matchId).stream()
            .filter(f -> FIGHT_FACTOR.equals(f.getFactorType()) && f.getSpikeIndex() != null)
            .toList();
    List<FightExplanationDomain> rows = new ArrayList<>();
    Set<DeathEventDomain> usedEvents = Collections.newSetFromMap(new IdentityHashMap<>());
    List<FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getFightIndex() != null)
            .sorted(Comparator.comparing(FightDomain::getFightIndex))
            .toList();
    for (FightDomain fight : fights) {
      TeamSide winner = Enums.find(TeamSide.class, fight.getWinner());
      if (winner == null) {
        continue;
      }
      TeamSide loser = winner == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
      int index = fight.getFightIndex();
      Gold gold = goldLost(loser, deaths.getOrDefault(index, List.of()), deathEvents, usedEvents);
      rows.add(
          FightExplanationDomain.builder()
              .matchId(matchId)
              .fightIndex(index)
              .spikeIndex(swingOf(index, winner, fightFactors, spikes))
              .losingTeam(loser.name())
              .goldLost(gold.lost())
              .unmatchedDeaths(gold.unmatched())
              .reasonCount(
                  (int)
                      reasons.getOrDefault(index, List.of()).stream()
                          .filter(r -> loser.name().equals(r.getTeam()))
                          .count())
              .analysisVersion(version())
              .build());
    }
    explanationRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    explanationRepo.deleteByMatchId(matchId);
  }

  /** The swing that lists the fight; where two do, the one the fight's winner benefited from. */
  private static Integer swingOf(
      int fightIndex,
      TeamSide winner,
      List<GameSpikeFactorDomain> factors,
      Map<Integer, GameSpikeDomain> spikes) {
    List<Integer> candidates =
        factors.stream()
            .filter(f -> String.valueOf(fightIndex).equals(f.getRef()))
            .map(GameSpikeFactorDomain::getSpikeIndex)
            .filter(spikes::containsKey)
            .sorted()
            .toList();
    Optional<Integer> benefiting =
        candidates.stream()
            .filter(i -> winner.name().equals(spikes.get(i).getBenefitingTeam()))
            .findFirst();
    return benefiting.orElse(candidates.isEmpty() ? null : candidates.get(0));
  }

  /** The gold of the matched deaths (null when none matched) and how many deaths found no match. */
  private record Gold(Long lost, int unmatched) {}

  /**
   * What OpenDota says the loser's deaths in the fight cost. Each OpenDota death is given to at
   * most one replay death, the nearest in time first, so two deaths of one hero in a few seconds
   * (an Aegis use) are not paid twice.
   */
  private Gold goldLost(
      TeamSide loser,
      List<DeathAnalysisDomain> deaths,
      List<DeathEventDomain> events,
      Set<DeathEventDomain> used) {
    List<DeathAnalysisDomain> own =
        deaths.stream()
            .filter(
                d ->
                    d.getPlayerSlot() != null
                        && d.getDeathSeconds() != null
                        && TeamSide.ofSlot(d.getPlayerSlot()) == loser)
            .toList();
    record Pair(DeathAnalysisDomain death, DeathEventDomain event, double gap) {}
    List<Pair> pairs = new ArrayList<>();
    for (DeathAnalysisDomain death : own) {
      for (DeathEventDomain event : events) {
        if (death.getPlayerSlot().equals(event.getPlayerSlot())
            && event.getGoldLost() != null
            && event.getDeathTime() != null
            && !used.contains(event)) {
          double gap = Math.abs(event.getDeathTime() - death.getDeathSeconds());
          if (gap <= properties.getDeathMatchSeconds()) {
            pairs.add(new Pair(death, event, gap));
          }
        }
      }
    }
    pairs.sort(Comparator.comparingDouble(Pair::gap));
    Set<DeathAnalysisDomain> matched = Collections.newSetFromMap(new IdentityHashMap<>());
    long gold = 0;
    for (Pair pair : pairs) {
      if (!matched.contains(pair.death()) && used.add(pair.event())) {
        matched.add(pair.death());
        gold += pair.event().getGoldLost();
      }
    }
    return new Gold(matched.isEmpty() ? null : gold, own.size() - matched.size());
  }

  private static <T> Map<Integer, List<T>> group(List<T> rows, Function<T, Integer> fightIndex) {
    return rows.stream()
        .filter(r -> fightIndex.apply(r) != null)
        .collect(Collectors.groupingBy(fightIndex));
  }
}
