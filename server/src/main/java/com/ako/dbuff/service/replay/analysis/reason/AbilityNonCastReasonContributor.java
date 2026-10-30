package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.service.replay.analysis.fight.NonCastReason;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mirrors the abilities and items a hero could not cast for lack of mana or because they were
 * cooling down; one reason per hero and code, listing what it covers.
 */
@Component
@RequiredArgsConstructor
public class AbilityNonCastReasonContributor implements ReasonContributor {
  private static final String ITEM = "ITEM";

  private final ReasonProperties properties;
  private final SkillFunctions skills;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    Map<String, Map<Long, List<FightAbilityUseDomain>>> byCodeAndPlayer = new LinkedHashMap<>();
    for (FightAbilityUseDomain use : context.abilityUses()) {
      String code = code(use);
      if (code != null && use.getPlayerSlot() != null) {
        byCodeAndPlayer
            .computeIfAbsent(code, k -> new LinkedHashMap<>())
            .computeIfAbsent(use.getPlayerSlot(), k -> new ArrayList<>())
            .add(use);
      }
    }
    List<Reason> reasons = new ArrayList<>();
    byCodeAndPlayer.forEach(
        (code, byPlayer) ->
            byPlayer.forEach((slot, uses) -> reasons.add(reason(context, code, slot, uses))));
    return reasons;
  }

  private Reason reason(
      FightContext context, String code, long slot, List<FightAbilityUseDomain> uses) {
    double base =
        code.equals("NO_MANA")
            ? properties.getNoManaSeverity()
            : properties.getOnCooldownSeverity();
    List<String> names =
        uses.stream().map(FightAbilityUseDomain::getAbilityName).distinct().toList();
    double severity = base + properties.getRepeatedUseStep() * (names.size() - 1);
    if (context.presenceOf(slot) == Presence.PRESENT_PASSIVE) {
      severity *= properties.getPassivePresenceFactor();
    }
    return Reason.player(Reason.COMMON, code, slot, severity, String.join(", ", names));
  }

  private String cooldownCode(FightAbilityUseDomain use) {
    if (ITEM.equals(use.getKind())) {
      return "ITEM_ON_COOLDOWN";
    }
    return skills.notCastable(use.getAbilityName()) ? null : "ABILITY_ON_COOLDOWN";
  }

  private String code(FightAbilityUseDomain use) {
    NonCastReason reason = Enums.find(NonCastReason.class, use.getNonCastReason());
    if (reason == null) {
      return null;
    }
    return switch (reason) {
      case NO_MANA -> "NO_MANA";
      case ON_COOLDOWN -> cooldownCode(use);
      case DEAD_EARLY, READY_NOT_CAST -> null;
    };
  }
}
