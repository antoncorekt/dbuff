package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * All Pick: a hero in the fight who played a final role his usual tags in the constants do not fit,
 * such as a hero tagged only as a support played as the carry.
 */
@Component
public class HeroRoleMismatchContributor extends ModeReasonContributor {
  static final String CODE = "HERO_ROLE_MISMATCH";

  private final ReasonProperties properties;
  private final HeroKits kits;

  public HeroRoleMismatchContributor(ReasonProperties properties, HeroKits kits) {
    super(AllPickProfile.MODE_ID, ALL_PICK);
    this.properties = properties;
    this.kits = kits;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.roles().stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .filter(r -> properties.getHeroRoleTags().containsKey(r.getRole()))
        .filter(r -> context.inFight(r.getPlayerSlot()))
        .map(r -> reason(context, r))
        .filter(Objects::nonNull)
        .toList();
  }

  private Reason reason(FightContext context, PlayerRoleDomain role) {
    long slot = role.getPlayerSlot();
    Optional<HeroConstant> hero = hero(kits, context, slot);
    List<String> tags = hero.map(HeroConstant::getRoles).orElse(List.of());
    Set<String> fitting = properties.getHeroRoleTags().get(role.getRole());
    if (tags.isEmpty() || tags.stream().anyMatch(fitting::contains)) {
      return null;
    }
    double severity =
        properties.byPresence(
            properties
                .getHeroRoleMismatchSeverity()
                .getOrDefault(role.getRole(), properties.getDefaultSeverity()),
            context.presenceOf(slot));
    return Reason.player(
        source(),
        CODE,
        slot,
        severity,
        "%s (%s) played %s"
            .formatted(
                heroName(hero, slot), String.join("/", tags), RoleLabels.of(role.getRole())));
  }
}
