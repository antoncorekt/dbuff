package com.ako.dbuff.service.replay.report;

/** How the report names an ability or item given its internal name. */
@FunctionalInterface
public interface AbilityLabels {
  /** The bare name, for comparing and for sentences that must not carry an icon. */
  String label(String internalName);

  /** The icon prefix of the ability or item, with its trailing space; empty when it has none. */
  default String icon(String internalName) {
    return "";
  }

  /** The name with its icon before it, for what the report shows. */
  default String decorated(String internalName) {
    String name = label(internalName);
    return name == null || name.isBlank() ? name : icon(internalName) + name;
  }
}
