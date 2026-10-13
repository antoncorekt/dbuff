package com.ako.dbuff.service.replay.analysis.lane;

/** A hero death; the killer is null when no hero did it. */
record LaneDeath(long victimSlot, Long killerSlot, double seconds) {}
