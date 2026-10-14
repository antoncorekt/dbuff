package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * For each fight, collects the reasons of every {@link ReasonContributor} that supports the match's
 * mode into {@code fight_reason}, from what the fight, ability and death analyzers stored. Where
 * two contributors give the same code for the same subject, the more severe one is kept. A full run
 * replaces every row of the match, those of {@link LateFightReasonAnalyzer} too, which runs again
 * after it.
 */
@Order(32)
@Component
@RequiredArgsConstructor
public class FightReasonAnalyzer implements ReplayAnalyzer {
  private final List<ReasonContributor> contributors;
  private final StoredMatchModes modes;
  private final FightContextLoader contextLoader;
  private final FightReasonRepo reasonRepo;

  @Override
  public String name() {
    return "fight-reasons";
  }

  @Override
  public int version() {
    return 4;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights", "fight-abilities", "deaths", "roles", "composition", "runes");
  }

  @Override
  public Set<String> requires(GameModeProfile mode) {
    if (mode.gameModeId() != AbilityDraftProfile.MODE_ID) {
      return requires();
    }
    Set<String> required = new HashSet<>(requires());
    required.add("ability-draft");
    return required;
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    if (mode == null) {
      return;
    }
    List<ReasonContributor> active = contributors.stream().filter(c -> c.supports(mode)).toList();
    List<FightReasonDomain> rows = new ArrayList<>();
    for (FightContext context : contextLoader.load(matchId, mode)) {
      List<Reason> reasons = active.stream().flatMap(c -> c.contribute(context).stream()).toList();
      rows.addAll(
          FightReasonRows.strongestPerSubject(
              matchId, context.fight().getFightIndex(), reasons, version()));
    }
    reasonRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    reasonRepo.deleteByMatchId(matchId);
  }
}
