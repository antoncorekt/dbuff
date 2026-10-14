package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;

/** What a complete Ability Draft looks like, asked by every extractor so their tables agree. */
final class DraftShape {
  private DraftShape() {}

  static boolean skillsComplete(AbilityDraftFacts draft, AbilityDraftProfile profile) {
    return draft != null
        && draft.pool().size() == profile.poolSize()
        && draft.picks().size() == profile.picks();
  }

  static boolean heroesComplete(HeroDraftFacts draft, AbilityDraftProfile profile) {
    return draft != null
        && draft.pool().size() == profile.heroPoolSize()
        && draft.picks().size() == profile.heroPicks();
  }
}
