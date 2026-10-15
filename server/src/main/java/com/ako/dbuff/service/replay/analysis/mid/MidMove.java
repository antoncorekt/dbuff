package com.ako.dbuff.service.replay.analysis.mid;

/**
 * His first move after level 6: when, and the lane or JUNGLE he went to (null for a fight).
 *
 * @param seconds null for STAYED_MID
 */
record MidMove(MidNext next, Integer seconds, String place) {
  static final MidMove STAYED = new MidMove(MidNext.STAYED_MID, null, null);
}
