package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneEvidence;
import com.ako.dbuff.service.replay.analysis.rune.RuneProperties;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A hero who took a power rune whose effect covers the start of the fight or part of it (by the
 * modifier events of the hero, which a death or a removal ends; the duration property when the log
 * has none for the pickup), and who took part in the fight, gives his team {@code RUNE_ADVANTAGE}
 * whatever the result, and the other team {@code ENEMY_RUNE_ADVANTAGE}, so the losing side's
 * explanation says it.
 */
@Component
@RequiredArgsConstructor
public class RuneAdvantageReasonContributor implements ReasonContributor {
  static final String RUNE_ADVANTAGE = "RUNE_ADVANTAGE";
  static final String ENEMY_RUNE_ADVANTAGE = "ENEMY_RUNE_ADVANTAGE";
  private static final int SECONDS_PER_MINUTE = 60;

  private final ReasonProperties properties;
  private final RuneProperties runeProperties;
  private final HeroKits kits;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    FightDomain fight = context.fight();
    if (fight == null || fight.getStartSeconds() == null) {
      return List.of();
    }
    double start = fight.getStartSeconds();
    double end = fight.getEndSeconds() == null ? start : fight.getEndSeconds();
    Map<Long, List<Active>> byTaker = new TreeMap<>();
    for (RunePickupDomain pickup : context.runes()) {
      RuneType type = Enums.find(RuneType.class, pickup.getRuneType());
      if (type == null
          || !type.isFightEffect()
          || pickup.getPlayerSlot() == null
          || pickup.getSeconds() == null
          || !context.inFight(pickup.getPlayerSlot())
          || pickup.getSeconds() > end
          || effectEnd(pickup, type) < start) {
        continue;
      }
      long slot = pickup.getPlayerSlot();
      String evidence =
          new RuneEvidence(
                  type.words(),
                  clock(pickup.getSeconds()),
                  ModeReasonContributor.heroName(
                      ModeReasonContributor.hero(kits, context, slot), slot),
                  (int) Math.round(start - pickup.getSeconds()))
              .text();
      byTaker
          .computeIfAbsent(slot, k -> new ArrayList<>())
          .add(new Active(evidence, properties.severity(type)));
    }
    List<Reason> reasons = new ArrayList<>();
    Map<TeamSide, List<Active>> byTeam = new EnumMap<>(TeamSide.class);
    byTaker.forEach(
        (slot, runes) -> {
          Active all = combine(runes);
          reasons.add(
              Reason.player(Reason.COMMON, RUNE_ADVANTAGE, slot, all.severity(), all.evidence()));
          byTeam.computeIfAbsent(TeamSide.ofSlot(slot), k -> new ArrayList<>()).addAll(runes);
        });
    byTeam.forEach(
        (team, runes) -> {
          Active all = combine(runes);
          reasons.add(
              Reason.team(
                  Reason.COMMON,
                  ENEMY_RUNE_ADVANTAGE,
                  team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT,
                  all.severity(),
                  all.evidence()));
        });
    return reasons;
  }

  /** One rune of the fight: the evidence text and its own severity. */
  private record Active(String evidence, double severity) {}

  /**
   * The runes of one reason as one: all of them in the evidence, the strongest severity plus a step
   * for each further rune, so a second rune never replaces the first.
   */
  private Active combine(List<Active> runes) {
    double strongest = runes.stream().mapToDouble(Active::severity).max().orElse(0);
    return new Active(
        runes.stream().map(Active::evidence).collect(Collectors.joining(RuneEvidence.SEPARATOR)),
        strongest + properties.getRuneAdvantageExtraStep() * (runes.size() - 1));
  }

  /** The end of the modifier span when the log has one, else the pickup plus the duration. */
  private double effectEnd(RunePickupDomain pickup, RuneType type) {
    return pickup.getEffectEndSeconds() != null
        ? pickup.getEffectEndSeconds()
        : pickup.getSeconds() + runeProperties.effectSeconds(type);
  }

  private static String clock(int seconds) {
    return "%d:%02d".formatted(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
  }
}
