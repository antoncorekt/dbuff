package com.ako.dbuff.service.replay.analysis.creep;

import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;

/**
 * One creep a hero killed. Gold and experience are what the combat log says that hero received with
 * the kill, null where they could not be tied to it.
 *
 * @param side the creep's side seen from the killer; null for a camp creep
 * @param denied the killer's own side's lane creep
 * @param gold the gold received; null for a deny or when the entry could not be told
 * @param bonusGold what a flagbearer paid the killer on top; null for every other creep
 * @param xp the experience the killer received
 * @param xpRecipients how many heroes received experience at that moment
 * @param x where the gold or experience entry says the kill was; null when there was none
 */
public record CreepKill(
    String hero,
    double second,
    CreepClass creepClass,
    String creepName,
    ActivitySegment.Side side,
    boolean denied,
    Integer gold,
    Integer bonusGold,
    Integer xp,
    Integer xpRecipients,
    Integer x,
    Integer y) {}
