package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.SkillCountRepository;
import com.ako.dbuff.dao.repo.SkillCountRepository.DamageAverage;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillWindow;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a {@code missed_pick} row for each skill a player took while a clearly stronger one of the
 * same slot kind was still free and went to the enemy later: it did more total damage in the match
 * and was cast in an important fight its team won. Skills are compared like with like (see {@link
 * MissedPickFinder}) and each enemy skill is flagged once. Reads the stored draft, the match's
 * ability damage and the fight tables, plus the mean damage of both skills over earlier Ability
 * Draft games of the same patch. That average is context only; it never decides a flag, it marks a
 * candidate far above it as carried.
 */
@Slf4j
@Order(97)
@Component
@RequiredArgsConstructor
public class MissedPickAnalyzer implements ReplayAnalyzer {
  private static final String AD_SUFFIX = "_ad";

  private final AbilityDraftPickRepo pickRepo;
  private final AbilityRepo abilityRepo;
  private final FightImportanceRepo importanceRepo;
  private final FightAbilityUseRepo abilityUseRepo;
  private final MissedPickRepo missedPickRepo;
  private final MissedPickProperties properties;
  private final MatchRepo matchRepo;
  private final SkillCountRepository countRepository;
  private final AbilityDraftProfile draftProfile;
  private final SkillFunctions skillFunctions;

  @Override
  public String name() {
    return "missed-pick";
  }

  @Override
  public int version() {
    return 4;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == draftProfile.gameModeId();
  }

  @Override
  public Set<String> requires() {
    return Set.of("ability-draft", "important-fights", "fight-abilities");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<AbilityDraftPickDomain> picks = pickRepo.findByMatchId(matchId);
    long usable = picks.stream().filter(MissedPickFinder::complete).count();
    if (usable < picks.size()) {
      log.info(
          "Match {}: {} of {} draft picks lack a turn number or other field and are skipped",
          matchId,
          picks.size() - usable,
          picks.size());
    }
    if (usable == 0) {
      return;
    }
    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(matchId);
    if (abilities.isEmpty()) {
      log.info("Match {} has no stored ability damage, so no missed picks", matchId);
      return;
    }
    Map<String, Long> damage = new HashMap<>();
    abilities.forEach(
        a ->
            damage.merge(
                key(a.getPlayerSlot(), a.getAbilityId()), orZero(a.getDamageDealt()), Long::sum));
    Map<String, Long> byName = new HashMap<>();
    abilities.stream()
        .filter(a -> a.getName() != null)
        .forEach(
            a ->
                byName.merge(
                    nameKey(a.getPlayerSlot(), a.getName()),
                    orZero(a.getDamageDealt()),
                    Long::sum));
    WonFightCasts casts =
        new WonFightCasts(
            importanceRepo.findByMatchId(matchId), abilityUseRepo.findByMatchId(matchId));
    List<MissedPickDomain> rows =
        new MissedPickFinder(properties.getMinRatio(), skillFunctions)
            .find(
                matchId,
                picks,
                (slot, abilityId) -> damageOf(damage, byName, picks, slot, abilityId),
                casts,
                version());
    addPatchAverages(matchId, picks, abilities, rows);
    missedPickRepo.saveAll(rows);
  }

  private void addPatchAverages(
      long matchId,
      List<AbilityDraftPickDomain> picks,
      List<AbilityDomain> abilities,
      List<MissedPickDomain> rows) {
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (rows.isEmpty()
        || match == null
        || match.getPatch() == null
        || match.getStartTime() == null) {
      return;
    }
    Map<String, Long> ids = new HashMap<>();
    picks.forEach(p -> ids.putIfAbsent(p.getAbilityName(), damageAbilityId(p, abilities)));
    Set<Long> wanted = new HashSet<>();
    rows.forEach(
        r -> {
          wanted.add(ids.get(r.getTakenAbility()));
          wanted.add(ids.get(r.getCandidateAbility()));
        });
    Map<Long, DamageAverage> averages =
        countRepository.averageDamage(
            wanted, match.getPatch(), new SkillWindow(0, match.getStartTime(), matchId));
    for (MissedPickDomain row : rows) {
      DamageAverage taken = averages.get(ids.get(row.getTakenAbility()));
      DamageAverage candidate = averages.get(ids.get(row.getCandidateAbility()));
      row.setTakenPatchAverage(taken == null ? null : taken.average());
      row.setCandidatePatchAverage(candidate == null ? null : candidate.average());
      row.setCandidatePatchGames(candidate == null ? 0 : (int) candidate.games());
      row.setCarried(
          candidate != null
              && row.getCandidateDamage() > properties.getCarriedRatio() * candidate.average());
    }
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    missedPickRepo.deleteByMatchId(matchId);
  }

  /** The id the damage rows use: an {@code _ad} pick's damage is stored under its base skill. */
  private static Long damageAbilityId(AbilityDraftPickDomain pick, List<AbilityDomain> abilities) {
    String name = pick.getAbilityName();
    if (name == null
        || !name.endsWith(AD_SUFFIX)
        || abilities.stream()
            .anyMatch(a -> Objects.equals(a.getAbilityId(), pick.getAbilityId()))) {
      return pick.getAbilityId();
    }
    String base = name.substring(0, name.length() - AD_SUFFIX.length());
    return abilities.stream()
        .filter(a -> base.equals(a.getName()))
        .map(AbilityDomain::getAbilityId)
        .findFirst()
        .orElse(pick.getAbilityId());
  }

  private static long damageOf(
      Map<String, Long> damage,
      Map<String, Long> byName,
      List<AbilityDraftPickDomain> picks,
      long slot,
      long abilityId) {
    Long own = damage.get(key(slot, abilityId));
    if (own != null) {
      return own;
    }
    return picks.stream()
        .filter(p -> p.getAbilityId() != null && p.getAbilityId() == abilityId)
        .map(AbilityDraftPickDomain::getAbilityName)
        .filter(n -> n != null && n.endsWith(AD_SUFFIX))
        .findFirst()
        .map(n -> n.substring(0, n.length() - AD_SUFFIX.length()))
        .map(base -> byName.getOrDefault(nameKey(slot, base), 0L))
        .orElse(0L);
  }

  private static String nameKey(Long slot, String name) {
    return slot + "/" + name;
  }

  private static String key(Long slot, Long abilityId) {
    return slot + "/" + abilityId;
  }

  private static long orZero(Long value) {
    return value == null ? 0 : value;
  }
}
