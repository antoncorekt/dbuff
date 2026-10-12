package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bound from {@code replay.analysis.composition.*}: the thresholds of the composition rules, all
 * hypotheses for the learning loop.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay.analysis.composition")
public class CompositionProperties {
  /** A player with at least this farm priority is a carry. */
  private double carryFarm = 0.6;

  /** A player with at most this farm priority is a support. */
  private double supportFarm = 0.25;

  /** The number of carries is a finding when it is 0 or at least this. */
  private int carriesNotedFrom = 3;

  /** The number of supports is a finding when it is 0 or at least this. */
  private int supportsNotedFrom = 4;

  /** A team where nobody reaches this farm priority has no farm engine. */
  private double farmEngine = 0.5;

  /** A team where everybody has at least this farm priority is top heavy. */
  private double topHeavyFarm = 0.5;

  /** The value some player must reach for the team to have the function. */
  private Map<RoleFunction, Double> minimum = defaultMinimum();

  /** The players whose played role differs from the intended one that make INTENT_NOT_PLAYED. */
  private int intentNotPlayedPlayers = 1;

  @PostConstruct
  public void validate() {
    for (CompositionToken token : CompositionToken.values()) {
      if (token.function() != null && minimum.get(token.function()) == null) {
        throw new IllegalStateException(
            "replay.analysis.composition.minimum has no value for " + token.function());
      }
    }
  }

  private static Map<RoleFunction, Double> defaultMinimum() {
    Map<RoleFunction, Double> minimum = new EnumMap<>(RoleFunction.class);
    minimum.put(RoleFunction.INITIATION, 0.4);
    minimum.put(RoleFunction.DISABLE, 0.4);
    minimum.put(RoleFunction.DAMAGE, 0.4);
    minimum.put(RoleFunction.DURABILITY, 0.4);
    minimum.put(RoleFunction.SAVE_HEAL, 0.3);
    minimum.put(RoleFunction.VISION_DUTY, 0.3);
    return minimum;
  }
}
