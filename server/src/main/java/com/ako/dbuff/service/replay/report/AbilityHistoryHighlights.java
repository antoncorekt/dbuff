package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Abilities of the channel's tracked players that did far more damage than their own average over
 * earlier games. Not a section: it only feeds the highlights card.
 */
@Component
@RequiredArgsConstructor
public class AbilityHistoryHighlights implements HighlightSource {
  static final long MIN_GAMES = 5;
  static final long MIN_DAMAGE = 1000;

  private final AbilityRepo abilityRepo;
  private final ReplayReportProperties properties;

  @Override
  public List<Highlight> highlights(ReplayReportInput input) {
    if (input.instance() == null) {
      return List.of();
    }
    Set<Long> tracked = input.instance().getPlayerIds();
    if (tracked.isEmpty()) {
      return List.of();
    }
    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(input.matchId());
    if (abilities == null) {
      return List.of();
    }
    List<Highlight> result = new ArrayList<>();
    for (Long playerId : tracked) {
      List<Object[]> averages =
          abilityRepo.findAvgStatsByPlayerAbilities(playerId, input.matchId(), input.matchId());
      if (averages == null) {
        continue;
      }
      for (Object[] avg : averages) {
        abilities.stream()
            .filter(a -> Objects.equals(a.getPlayerId(), playerId))
            .filter(a -> Objects.equals(a.getAbilityId(), avg[0]))
            .findFirst()
            .flatMap(a -> highlight(a, avg))
            .ifPresent(result::add);
      }
    }
    return result;
  }

  private Optional<Highlight> highlight(AbilityDomain current, Object[] avg) {
    long games = avg[6] instanceof Number n ? n.longValue() : 0;
    double average = avg[3] instanceof Number n ? n.doubleValue() : 0;
    long damage = current.getDamageDealt() == null ? 0 : current.getDamageDealt();
    if (games < MIN_GAMES || average <= 0 || damage < MIN_DAMAGE) {
      return Optional.empty();
    }
    double ratio = damage / average;
    String name = current.getPrettyName() == null ? current.getName() : current.getPrettyName();
    String text =
        "%s did %s damage, %.1fx this player's average of %s over %d games"
            .formatted(
                name,
                MissedPickWording.damage(damage),
                ratio,
                MissedPickWording.damage(Math.round(average)),
                games);
    return Optional.of(
        new Highlight(
            "ability-history",
            null,
            Highlight.scale(ratio, properties.getHighlights().getAbilityHistory()),
            text));
  }
}
