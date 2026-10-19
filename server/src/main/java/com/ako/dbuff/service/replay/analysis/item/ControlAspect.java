package com.ako.dbuff.service.replay.analysis.item;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * An item used on an enemy who died within the kill window earns the disable share of that kill
 * (Force Staff, Eul's).
 */
@Component
public class ControlAspect implements ItemAspect {
  public static final String NAME = "control";

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
    return TargetUses.of(context).control();
  }
}
