package com.ako.dbuff.service.replay.report;

import java.util.Set;

/** The words that tell a chart reader its heroes are not the channel's tracked players. */
final class FallbackNote {
  private FallbackNote() {}

  /**
   * @param noneOfYours what none of the tracked players did, such as "farmed"
   * @param shown what is shown instead, such as "the top farmers"
   */
  static String of(Set<Long> focusSlots, String noneOfYours, String shown) {
    String why =
        focusSlots == null || focusSlots.isEmpty()
            ? "No players are tracked"
            : "None of your players " + noneOfYours;
    return why + "; showing " + shown + ". ";
  }
}
