package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.service.replay.analysis.fight.AbsenceReason;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mirrors why a living hero far from the fight did not teleport in. */
@Component
@RequiredArgsConstructor
public class AbsenceReasonContributor implements ReasonContributor {
  private final ReasonProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    return context.players().stream()
        .filter(p -> p.getPlayerSlot() != null && p.getPresence() != null)
        .filter(p -> Enums.find(AbsenceReason.class, p.getAbsenceReason()) != null)
        .map(this::reason)
        .toList();
  }

  private Reason reason(FightPlayerDomain player) {
    AbsenceReason absence = Enums.find(AbsenceReason.class, player.getAbsenceReason());
    String evidence =
        player.getTeleportCooldownLeft() == null || player.getTeleportCooldownLeft() == 0
            ? player.getPresence()
            : "%s, teleport ready in %d s"
                .formatted(player.getPresence(), player.getTeleportCooldownLeft());
    return Reason.player(
        Reason.COMMON,
        absence.name(),
        player.getPlayerSlot(),
        properties.severity(absence),
        evidence);
  }
}
