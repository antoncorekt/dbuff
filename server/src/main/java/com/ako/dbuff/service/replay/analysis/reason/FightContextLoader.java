package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads what is stored about the fights of a match into one {@link FightContext} per fight. */
@Component
@RequiredArgsConstructor
public class FightContextLoader {
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final FightAbilityUseRepo abilityUseRepo;
  private final DeathAnalysisRepo deathRepo;
  private final PlayerRoleRepo roleRepo;
  private final TeamCompositionRepo compositionRepo;
  private final PlayerRoleProfileRepo roleProfileRepo;
  private final PlayerGameStatisticRepo statisticRepo;
  private final AbilityDraftPoolRepo poolRepo;
  private final RunePickupRepo runePickupRepo;

  /** The fights that have an index, in storage order. */
  public List<FightContext> load(long matchId, GameModeProfile mode) {
    Map<Integer, List<FightPlayerDomain>> players =
        group(fightPlayerRepo.findByMatchId(matchId), FightPlayerDomain::getFightIndex);
    Map<Integer, List<FightAbilityUseDomain>> uses =
        group(abilityUseRepo.findByMatchId(matchId), FightAbilityUseDomain::getFightIndex);
    Map<Integer, List<DeathAnalysisDomain>> deaths =
        group(deathRepo.findByMatchId(matchId), DeathAnalysisDomain::getFightIndex);
    List<PlayerRoleDomain> roles = roleRepo.findByMatchId(matchId);
    List<TeamCompositionDomain> compositions = compositionRepo.findByMatchId(matchId);
    List<PlayerRoleProfileDomain> profiles = roleProfileRepo.findByMatchId(matchId);
    Map<Long, Long> heroIds =
        statisticRepo.findAllByMatchId(matchId).stream()
            .filter(s -> s.getPlayerSlot() != null && s.getHeroId() != null)
            .collect(
                Collectors.toMap(
                    PlayerMatchStatisticDomain::getPlayerSlot,
                    PlayerMatchStatisticDomain::getHeroId,
                    (first, second) -> first));
    List<AbilityDraftPoolDomain> pool = poolRepo.findByMatchId(matchId);
    List<RunePickupDomain> runes = runePickupRepo.findByMatchId(matchId);
    List<FightContext> contexts = new ArrayList<>();
    for (FightDomain fight : fightRepo.findByMatchId(matchId)) {
      if (fight.getFightIndex() == null) {
        continue;
      }
      int index = fight.getFightIndex();
      contexts.add(
          new FightContext(
              matchId,
              mode,
              fight,
              players.getOrDefault(index, List.of()),
              uses.getOrDefault(index, List.of()),
              deaths.getOrDefault(index, List.of()),
              roles,
              compositions,
              profiles,
              heroIds,
              pool,
              runes));
    }
    return contexts;
  }

  private static <T> Map<Integer, List<T>> group(List<T> rows, Function<T, Integer> fightIndex) {
    return rows.stream()
        .filter(r -> fightIndex.apply(r) != null)
        .collect(Collectors.groupingBy(fightIndex));
  }
}
