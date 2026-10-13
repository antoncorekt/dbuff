package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;

/** A hero who has a lane, with the window he laned in. */
record LaneHero(long slot, TeamSide team, Zone.Lane lane, LaningWindow window) {}
