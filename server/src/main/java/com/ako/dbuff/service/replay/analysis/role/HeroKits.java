package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Optional;

/** Looks up what OpenDota's constants say about a hero and an ability. */
public interface HeroKits {
  Optional<HeroConstant> hero(long heroId);

  Optional<AbilityConstant> ability(String name);
}
