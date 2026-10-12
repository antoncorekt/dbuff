package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.service.replay.analysis.fight.NonCastReason;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    Map<String, Map<Long, List<FightAbilityUseDomain>>> byCodeAndPlayer = new LinkedHashMap<>();
    for (FightAbilityUseDomain use : context.abilityUses()) {
      String code = code(use);
      if (code != null) {
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
    double severity = base + properties.getRepeatedUseStep() * (uses.size() - 1);
    if (context.presenceOf(slot) == Presence.PRESENT_PASSIVE) {
      severity *= properties.getPassivePresenceFactor();
    }
    String evidence =
        uses.stream().map(FightAbilityUseDomain::getAbilityName).collect(Collectors.joining(", "));
    return Reason.player(Reason.COMMON, code, slot, severity, evidence);
  }

  private static String code(FightAbilityUseDomain use) {
    if (use.getNonCastReason() == null) {
      return null;
    }
    return switch (NonCastReason.valueOf(use.getNonCastReason())) {
      case NO_MANA -> "NO_MANA";
      case ON_COOLDOWN -> ITEM.equals(use.getKind()) ? "ITEM_ON_COOLDOWN" : "ABILITY_ON_COOLDOWN";
      case DEAD_EARLY, READY_NOT_CAST -> null;
    };
  }
}
