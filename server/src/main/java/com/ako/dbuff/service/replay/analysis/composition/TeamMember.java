package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;

/**
 * One player of the team being judged.
 *
 * @param role the role he has at the judged stage
 * @param intentRole the role the draft meant for him, null when there is no intent for the team
 */
public record TeamMember(long slot, int role, Integer intentRole, FunctionProfile profile) {}
