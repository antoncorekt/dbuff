package com.ako.dbuff.service.replay.analysis.fightloss;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
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
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stored explanations of a match as a tree: swings, their lost fights, and each fight's ranked
 * reasons. Storage is uncapped; a reader that needs a short text passes its limits here. {@link
 * #read} tells only the important fights (all of them when the match has no importance stored);
 * {@link #readAll} also returns the others, with their importance.
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
  private final FightImportanceRepo importanceRepo;
  private final FightLossProperties properties;

  /**
   * A reason in its rank order, 1 being the one to tell first.
   *
   * @param also the codes of the same hero's other absence reasons this entry stands for
   * @param ranked false for a stored reason left out of the ranking (rank 0), kept so a reviewer
   *     still sees it; a reader that tells the ranking must filter on this
   */
  public record RankedReason(
      int rank, FightReasonDomain reason, List<String> also, boolean ranked) {}

  /**
   * @param importance the fight's importance row, null when none is stored
   * @param reasons the top ranked reasons, then every reason that is not ranked
   * @param totalReasons all reasons stored for the losing team, not ranked entries: absence reasons
   *     collapsed into an entry, unranked reasons and those cut by {@code topReasons} all count
   */
  public record LostFight(
      FightDomain fight,
      FightExplanationDomain explanation,
      List<RankedReason> reasons,
      int totalReasons,
      FightImportanceDomain importance) {
    public LostFight(
        FightDomain fight,
        FightExplanationDomain explanation,
        List<RankedReason> reasons,
        int totalReasons) {
      this(fight, explanation, reasons, totalReasons, null);
    }

    /**
     * A fight of a match with no importance stored at all counts as important, so old matches read
     * as before.
     */
    public boolean important() {
      return importance == null || importance.isImportant();
    }
  }

  /**
   * @param noImportantFight true when none of the swing's lost fights is important and {@code
   *     fights} holds its costliest one instead, so a swing is never told without a fight
   */
  public record SwingExplanation(
      GameSpikeDomain swing, List<LostFight> fights, boolean noImportantFight) {
    public SwingExplanation(GameSpikeDomain swing, List<LostFight> fights) {
      this(swing, fights, false);
    }
  }

  /**
   * @param swings every swing of the match, with the fights its losing team lost, costliest first
   *     and at most {@code maxFightsPerSwing} of them
   * @param otherFights every lost fight that belongs to no swing: outside any, or lost by the
   *     swing's benefiting team; in time order
   */
  public record MatchExplanation(List<SwingExplanation> swings, List<LostFight> otherFights) {}

  /**
   * The important lost fights only; every lost fight when the match has no importance stored. A
   * swing with no important lost fight keeps its costliest one, flagged.
   */
  @Transactional(readOnly = true)
  public MatchExplanation read(long matchId, int topReasons, int maxFightsPerSwing) {
    return read(matchId, topReasons, maxFightsPerSwing, true);
  }

  /** Every lost fight, each with its importance; for readers that list the unimportant ones. */
  @Transactional(readOnly = true)
  public MatchExplanation readAll(long matchId, int topReasons, int maxFightsPerSwing) {
    return read(matchId, topReasons, maxFightsPerSwing, false);
  }

  private MatchExplanation read(
      long matchId, int topReasons, int maxFightsPerSwing, boolean importantOnly) {
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> f.getFightIndex() != null)
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    Map<Integer, FightImportanceDomain> importance =
        importanceRepo.findByMatchId(matchId).stream()
            .filter(i -> i.getFightIndex() != null && fights.containsKey(i.getFightIndex()))
            .collect(
                Collectors.toMap(
                    FightImportanceDomain::getFightIndex, Function.identity(), (a, b) -> a));
    Map<Integer, List<FightReasonDomain>> reasons =
        reasonRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getFightIndex() != null)
            .collect(Collectors.groupingBy(FightReasonDomain::getFightIndex));
    List<LostFight> lost =
        explanationRepo.findByMatchId(matchId).stream()
            .filter(e -> e.getFightIndex() != null && fights.containsKey(e.getFightIndex()))
            .sorted(Comparator.comparing(FightExplanationDomain::getFightIndex))
            .map(
                e ->
                    lostFight(
                        fights.get(e.getFightIndex()),
                        e,
                        reasons,
                        topReasons,
                        importanceOf(e, importance)))
            .toList();
    List<GameSpikeDomain> spikes =
        spikeRepo.findByMatchId(matchId).stream()
            .filter(s -> s.getSpikeIndex() != null)
            .sorted(Comparator.comparing(GameSpikeDomain::getSpikeIndex))
            .toList();
    List<SwingExplanation> swings =
        spikes.stream().map(s -> swing(s, lost, maxFightsPerSwing, importantOnly)).toList();
    List<LostFight> other =
        lost.stream()
            .filter(l -> !importantOnly || l.important())
            .filter(l -> spikes.stream().noneMatch(s -> belongsTo(s, l)))
            .toList();
    return new MatchExplanation(swings, other);
  }

  private static SwingExplanation swing(
      GameSpikeDomain swing, List<LostFight> lost, int maxFights, boolean importantOnly) {
    List<LostFight> own =
        lost.stream().filter(l -> belongsTo(swing, l)).sorted(COSTLIEST_FIRST).toList();
    List<LostFight> told = importantOnly ? own.stream().filter(LostFight::important).toList() : own;
    boolean none = told.isEmpty() && !own.isEmpty();
    return new SwingExplanation(
        swing,
        (none ? own.subList(0, 1) : told).stream().limit(Math.max(0, maxFights)).toList(),
        none);
  }

  /**
   * The stored importance; once a match has any, a decided fight without a row is not important.
   */
  private static FightImportanceDomain importanceOf(
      FightExplanationDomain explanation, Map<Integer, FightImportanceDomain> stored) {
    FightImportanceDomain row = stored.get(explanation.getFightIndex());
    if (row != null || stored.isEmpty()) {
      return row;
    }
    return FightImportanceDomain.builder()
        .matchId(explanation.getMatchId())
        .fightIndex(explanation.getFightIndex())
        .build();
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
      int topReasons,
      FightImportanceDomain importance) {
    List<FightReasonDomain> ofLoser =
        reasons.getOrDefault(explanation.getFightIndex(), List.of()).stream()
            .filter(r -> explanation.getLosingTeam().equals(r.getTeam()))
            .toList();
    ReasonRanking.Ranking ranking =
        ReasonRanking.rank(ofLoser, fight.getStartSeconds(), properties);
    int kept = Math.min(Math.max(0, topReasons), ranking.ranked().size());
    List<RankedReason> top =
        IntStream.range(0, kept)
            .mapToObj(
                i ->
                    new RankedReason(
                        i + 1,
                        ranking.ranked().get(i).reason(),
                        ranking.ranked().get(i).also(),
                        true))
            .toList();
    List<RankedReason> left =
        ranking.notRanked().stream().map(r -> new RankedReason(0, r, List.of(), false)).toList();
    return new LostFight(
        fight,
        explanation,
        Stream.concat(top.stream(), left.stream()).toList(),
        ofLoser.size(),
        importance);
  }
}
