package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.Optional;

/** A contributor of one game mode only; its reasons carry the mode's source name. */
abstract class ModeReasonContributor implements ReasonContributor {
  static final String ABILITY_DRAFT = "ABILITY_DRAFT";
  static final String ALL_PICK = "ALL_PICK";

  private final long modeId;
  private final String source;

  ModeReasonContributor(long modeId, String source) {
    this.modeId = modeId;
    this.source = source;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == modeId;
  }

  String source() {
    return source;
  }

  static Optional<HeroConstant> hero(HeroKits kits, FightContext context, long slot) {
    Long heroId = context.heroIds().get(slot);
    return heroId == null ? Optional.empty() : kits.hero(heroId);
  }

  static String heroName(Optional<HeroConstant> hero, long slot) {
    return hero.map(HeroConstant::getLocalized_name).orElse("slot " + slot);
  }
}
