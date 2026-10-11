package com.ako.dbuff.service.replay;

import java.util.Optional;

/** Resolves OpenDota ability ids to internal names. */
public interface AbilityNames {
  Optional<String> nameOf(long abilityId);
}
