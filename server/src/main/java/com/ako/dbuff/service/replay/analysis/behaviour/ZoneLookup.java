package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.map.Zone;

/** Classifies a map position; {@code MapLayout::zone} in production. */
@FunctionalInterface
public interface ZoneLookup {
  Zone at(double x, double y);
}
