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
import java.util.Comparator;
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
            .collect(Collectors.toMap(GameSpikeDomain::getSpikeIndex, Function.identity()));
    List<GameSpikeFactorDomain> fightFactors =
        factorRepo.findByMatchId(matchId).stream()
            .filter(f -> FIGHT_FACTOR.equals(f.getFactorType()))
            .toList();
    List<FightExplanationDomain> rows = new ArrayList<>();
    for (FightDomain fight : fightRepo.findByMatchId(matchId)) {
      TeamSide winner = Enums.find(TeamSide.class, fight.getWinner());
      if (winner == null) {
        continue;
      }
      TeamSide loser = winner == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
      int index = fight.getFightIndex();
      rows.add(
          FightExplanationDomain.builder()
              .matchId(matchId)
              .fightIndex(index)
              .spikeIndex(swingOf(index, winner, fightFactors, spikes))
              .losingTeam(loser.name())
              .goldLost(goldLost(loser, deaths.getOrDefault(index, List.of()), deathEvents))
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

  /** What OpenDota says the loser's deaths in the fight cost; null when none has a figure. */
  private Long goldLost(
      TeamSide loser, List<DeathAnalysisDomain> deaths, List<DeathEventDomain> events) {
    List<Long> figures = new ArrayList<>();
    for (DeathAnalysisDomain death : deaths) {
      if (death.getPlayerSlot() == null
          || death.getDeathSeconds() == null
          || TeamSide.ofSlot(death.getPlayerSlot()) != loser) {
        continue;
      }
      events.stream()
          .filter(e -> death.getPlayerSlot().equals(e.getPlayerSlot()))
          .filter(e -> e.getGoldLost() != null && e.getDeathTime() != null)
          .min(
              Comparator.comparingDouble(e -> Math.abs(e.getDeathTime() - death.getDeathSeconds())))
          .filter(
              e ->
                  Math.abs(e.getDeathTime() - death.getDeathSeconds())
                      <= properties.getDeathMatchSeconds())
          .ifPresent(e -> figures.add(e.getGoldLost()));
    }
    return figures.isEmpty() ? null : figures.stream().mapToLong(Long::longValue).sum();
  }

  private static <T> Map<Integer, List<T>> group(List<T> rows, Function<T, Integer> fightIndex) {
    return rows.stream()
        .filter(r -> fightIndex.apply(r) != null)
        .collect(Collectors.groupingBy(fightIndex));
  }
}
