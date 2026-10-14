package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.HeroDraftFacts;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import org.springframework.stereotype.Component;

@Component
class HeroDraftReaderFactory implements DraftReaderFactory<HeroDraftFacts> {
  @Override
  public long gameModeId() {
    return AbilityDraftProfile.MODE_ID;
  }

  @Override
  public DraftReader<HeroDraftFacts> create() {
    return new HeroDraftReader();
  }
}
