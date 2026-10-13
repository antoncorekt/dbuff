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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A hero who took a power rune whose effect covers the start of the fight or part of it, and who
 * took part in the fight, gives his team {@code RUNE_ADVANTAGE} whatever the result, and the other
 * team {@code ENEMY_RUNE_ADVANTAGE}, so the losing side's explanation says it.
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
    List<Reason> reasons = new ArrayList<>();
    for (RunePickupDomain pickup : context.runes()) {
      RuneType type = Enums.find(RuneType.class, pickup.getRuneType());
      if (type == null
          || !type.isFightEffect()
          || pickup.getPlayerSlot() == null
          || pickup.getSeconds() == null
          || !context.inFight(pickup.getPlayerSlot())
          || pickup.getSeconds() > end
          || pickup.getSeconds() + runeProperties.effectSeconds(type) < start) {
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
      double severity = properties.severity(type);
      TeamSide team = TeamSide.ofSlot(slot);
      reasons.add(Reason.player(Reason.COMMON, RUNE_ADVANTAGE, slot, severity, evidence));
      reasons.add(
          Reason.team(
              Reason.COMMON,
              ENEMY_RUNE_ADVANTAGE,
              team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT,
              severity,
              evidence));
    }
    return reasons;
  }

  private static String clock(int seconds) {
    return "%d:%02d".formatted(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
  }
}
