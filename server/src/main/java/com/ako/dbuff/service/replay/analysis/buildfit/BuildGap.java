package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;

/**
 * A function the build lacks for its role and the match backs.
 *
 * @param none the build has nothing of it, not just too little
 * @param evidence what in the match backs it
 * @param figure the lonely deaths for {@code DIED_ALONE}, the team place for {@code LOW_NET_WORTH}
 *     (x.5 for a tie of two)
 */
public record BuildGap(RoleFunction function, boolean none, Evidence evidence, double figure) {
  public enum Evidence {
    DIED_ALONE,
    LOW_NET_WORTH
  }
}
