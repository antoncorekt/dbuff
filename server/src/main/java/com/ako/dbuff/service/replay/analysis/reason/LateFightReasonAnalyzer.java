package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.fightloss.ReasonRanking;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The second pass of the fight reasons: the vision and rotation tables only exist after {@link
 * FightReasonAnalyzer} ({@code @Order(32)}) has run, and those analyzers must not move, so their
 * reasons are appended here once the vision, rotation and objective analyzers are done. It does not
 * require those tables: a contributor with no rows of its own for the match adds nothing, so a
 * failed rotations run does not cost the vision reasons and the other way round. It owns only the
 * rows of its contributors' sources, so a re-run is idempotent and the first pass's rows are never
 * touched. The readers rank {@code fight_reason} on read, so these rows compete with the others;
 * {@code fight_explanation.reason_count}, written by the loss analyzer before them, is recounted
 * here.
 */
@Order(95)
@Component
@RequiredArgsConstructor
public class LateFightReasonAnalyzer implements ReplayAnalyzer {
  private final List<LateReasonContributor> contributors;
  private final StoredMatchModes modes;
  private final FightContextLoader contextLoader;
  private final FightVisionRepo visionRepo;
  private final TeamRotationRepo rotationRepo;
  private final FightReasonRepo reasonRepo;
  private final FightExplanationRepo explanationRepo;

  @Override
  public String name() {
    return "late-fight-reasons";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights", "fight-reasons", "fight-loss");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    if (mode != null) {
      reasonRepo.saveAll(reasons(matchId, mode));
    }
    recountExplanations(matchId);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    List<String> sources = contributors.stream().map(LateReasonContributor::source).toList();
    if (!sources.isEmpty()) {
      reasonRepo.deleteByMatchIdAndSourceIn(matchId, sources);
    }
  }

  private List<FightReasonDomain> reasons(long matchId, GameModeProfile mode) {
    List<LateReasonContributor> active =
        contributors.stream().filter(c -> c.supports(mode)).toList();
    Map<Integer, List<FightVisionDomain>> visions =
        visionRepo.findByMatchId(matchId).stream()
            .filter(v -> v.getFightIndex() != null)
            .collect(Collectors.groupingBy(FightVisionDomain::getFightIndex));
    List<TeamRotationDomain> rotations = rotationRepo.findByMatchId(matchId);
    List<FightReasonDomain> rows = new ArrayList<>();
    for (FightContext fight : contextLoader.load(matchId, mode)) {
      int index = fight.fight().getFightIndex();
      LateFightContext context =
          new LateFightContext(fight, visions.getOrDefault(index, List.of()), rotations);
      List<Reason> found = active.stream().flatMap(c -> c.contribute(context).stream()).toList();
      rows.addAll(FightReasonRows.strongestPerSubject(matchId, index, found, version()));
    }
    return rows;
  }

  private void recountExplanations(long matchId) {
    List<FightExplanationDomain> explanations = explanationRepo.findByMatchId(matchId);
    if (explanations.isEmpty()) {
      return;
    }
    Map<Integer, List<FightReasonDomain>> reasons =
        reasonRepo.findByMatchId(matchId).stream()
            .filter(r -> r.getFightIndex() != null)
            .collect(Collectors.groupingBy(FightReasonDomain::getFightIndex));
    explanations.forEach(
        e ->
            e.setReasonCount(
                ReasonRanking.countOfTeam(
                    reasons.getOrDefault(e.getFightIndex(), List.of()), e.getLosingTeam())));
    explanationRepo.saveAll(explanations);
  }
}
