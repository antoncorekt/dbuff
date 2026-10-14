package com.ako.dbuff.service.replay.report;

/** How the report names an ability or item given its internal name. */
@FunctionalInterface
public interface AbilityLabels {
  String label(String internalName);
}
