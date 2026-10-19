package com.ako.dbuff.service.replay.analysis.item;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The casts of an item on a hero and whether they had an effect; the main aspect of the targeted
 * items, whose worth is in {@link SaveAspect} and {@link ControlAspect}.
 */
@Component
public class TargetUseAspect implements ItemAspect {
  public static final String NAME = "target-use";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return TargetUses.PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    return TargetUses.of(context).main();
  }
}
