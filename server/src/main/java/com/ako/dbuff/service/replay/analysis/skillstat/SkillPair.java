package com.ako.dbuff.service.replay.analysis.skillstat;

/** Two skills held together; the lower id is always first, so a pair has one spelling. */
public record SkillPair(long first, long second) {
  public static SkillPair of(long a, long b) {
    return a < b ? new SkillPair(a, b) : new SkillPair(b, a);
  }
}
