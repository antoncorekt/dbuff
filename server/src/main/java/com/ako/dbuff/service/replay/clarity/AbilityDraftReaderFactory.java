package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.AbilityDraftFacts;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import org.springframework.stereotype.Component;

@Component
class AbilityDraftReaderFactory implements DraftReaderFactory<AbilityDraftFacts> {
  private final AbilityDraftProfile profile;

  AbilityDraftReaderFactory(AbilityDraftProfile profile) {
    this.profile = profile;
  }

  @Override
  public long gameModeId() {
    return AbilityDraftProfile.MODE_ID;
  }

  @Override
  public DraftReader<AbilityDraftFacts> create() {
    return new AbilityDraftReader(profile.picks());
  }
}
