package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.Map;

/** What a good build holds for a role, as the least value of each function. */
public interface RoleNeeds {
  /** The minimum per function for the role 1 to 5; empty for a role with no needs. */
  Map<RoleFunction, Double> forRole(int role);
}
