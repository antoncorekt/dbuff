package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stored explanations of a match as a tree: swings, their lost fights, and each fight's ranked
 * reasons. Storage is uncapped; a reader that needs a short text passes its limits here.
 */
@Service
@RequiredArgsConstructor
public class FightExplanationReader {
  private static final Comparator<LostFight> COSTLIEST_FIRST =
      Comparator.comparing(
              (LostFight l) -> l.explanation().getGoldLost(),
              Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(l -> l.explanation().getFightIndex());

  private final GameSpikeRepo spikeRepo;
  private final FightRepo fightRepo;
  private final FightExplanationRepo explanationRepo;
  private final FightReasonRepo reasonRepo;
  private final FightLossProperties properties;

  /** A reason in its rank order, 1 being the one to tell first. */
  public record RankedReason(int rank, FightReasonDomain reason) {}

  /**
   * @param totalReasons the reasons stored for the losing team, whatever {@code topReasons} kept
   */
  public record LostFight(
      FightDomain fight,
      FightExplanationDomain explanation,
      List<RankedReason> reasons,
      int totalReasons) {}

  public record SwingExplanation(GameSpikeDomain swing, List<LostFight> fights) {}

  /**
   * @param swings every swing of the match, with the fights its losing team lost, costliest first
   *     and at most {@code maxFightsPerSwing} of them
   * @param otherFights every lost fight that belongs to no swing: outside any, or lost by the
   *     swing's benefiting team; in time order
   */
  public record MatchExplanation(List<SwingExplanation> swings, List<LostFight> otherFights) {}

  @Transactional(readOnly = true)
  public MatchExplanation read(long matchId, int topReasons, int maxFightsPerSwing) {
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getFightIndex() != null)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    Map<Integer, List<FightReasonDomain>> reasons =
        reasonRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getFightIndex() != null)
            .collect(Collectors.groupingBy(FightReasonDomain::getFightIndex));
    List<LostFight> lost =
        explanationRepo.findByMatchId(matchId).stream()
            .filter(e -> e.getFightIndex() != null && fights.containsKey(e.getFightIndex()))
            .sorted(Comparator.comparing(FightExplanationDomain::getFightIndex))
            .map(e -> lostFight(fights.get(e.getFightIndex()), e, reasons, topReasons))
            .toList();
    List<GameSpikeDomain> spikes =
        spikeRepo.findByMatchId(matchId).stream()
            .filter(s -> s.getSpikeIndex() != null)
            .sorted(Comparator.comparing(GameSpikeDomain::getSpikeIndex))
            .toList();
    List<SwingExplanation> swings =
        spikes.stream()
            .map(
                s ->
                    new SwingExplanation(
                        s,
                        lost.stream()
                            .filter(l -> belongsTo(s, l))
                            .sorted(COSTLIEST_FIRST)
                            .limit(Math.max(0, maxFightsPerSwing))
                            .toList()))
            .toList();
    List<LostFight> other =
        lost.stream().filter(l -> spikes.stream().noneMatch(s -> belongsTo(s, l))).toList();
    return new MatchExplanation(swings, other);
  }

  /** A swing is told by the fights its losing team lost, not by the ones it won. */
  private static boolean belongsTo(GameSpikeDomain swing, LostFight lost) {
    FightExplanationDomain e = lost.explanation();
    return swing.getSpikeIndex().equals(e.getSpikeIndex())
        && !Objects.equals(swing.getBenefitingTeam(), e.getLosingTeam());
  }

  private LostFight lostFight(
      FightDomain fight,
      FightExplanationDomain explanation,
      Map<Integer, List<FightReasonDomain>> reasons,
      int topReasons) {
    List<FightReasonDomain> ofLoser =
        reasons.getOrDefault(explanation.getFightIndex(), List.of()).stream()
            .filter(r -> explanation.getLosingTeam().equals(r.getTeam()))
            .toList();
    List<FightReasonDomain> ranked = ReasonRanking.rank(ofLoser, properties.getMinPlayerSeverity());
    List<RankedReason> top =
        IntStream.range(0, Math.min(topReasons, ranked.size()))
            .mapToObj(i -> new RankedReason(i + 1, ranked.get(i)))
            .toList();
    return new LostFight(fight, explanation, top, ranked.size());
  }
}
