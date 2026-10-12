package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Ability Draft: a hero in the fight whose INTENT role (what his skills were drafted for) differs
 * from the FINAL role he played; the further apart the roles, the more severe.
 */
@Component
public class IntentNotPlayedContributor extends ModeReasonContributor {
  static final String CODE = "INTENT_NOT_PLAYED";

  private final ReasonProperties properties;
  private final HeroKits kits;

  public IntentNotPlayedContributor(ReasonProperties properties, HeroKits kits) {
    super(AbilityDraftProfile.MODE_ID, ABILITY_DRAFT);
    this.properties = properties;
    this.kits = kits;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.profiles().stream()
        .filter(p -> RoleStage.INTENT.name().equals(p.getStage()))
        .filter(p -> p.getPlayerSlot() != null && p.getRole() != null)
        .filter(p -> context.inFight(p.getPlayerSlot()))
        .map(intent -> reason(context, intent))
        .filter(Objects::nonNull)
        .toList();
  }

  private Reason reason(FightContext context, PlayerRoleProfileDomain intent) {
    long slot = intent.getPlayerSlot();
    Integer played =
        context.profile(slot, RoleStage.FINAL).map(PlayerRoleProfileDomain::getRole).orElse(null);
    if (played == null || played.equals(intent.getRole())) {
      return null;
    }
    int distance = Math.abs(played - intent.getRole());
    double severity =
        properties.byPresence(
            properties.getIntentNotPlayedBase()
                + properties.getIntentNotPlayedDistanceStep() * (distance - 1),
            context.presenceOf(slot));
    String hero = heroName(hero(kits, context, slot), slot);
    return Reason.player(
        source(),
        CODE,
        slot,
        severity,
        "%s was drafted as %s and played %s"
            .formatted(hero, RoleLabels.of(intent.getRole()), RoleLabels.of(played)));
  }
}
