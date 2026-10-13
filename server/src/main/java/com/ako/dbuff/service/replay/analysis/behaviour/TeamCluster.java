package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.map.Zone;
import java.util.Set;

/**
 * The biggest group of a team's alive heroes at one moment: who, where its centre is and its zone.
 */
public record TeamCluster(Set<Long> members, double x, double y, Zone zone) {}
