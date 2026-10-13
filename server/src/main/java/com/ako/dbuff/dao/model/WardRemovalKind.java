package com.ako.dbuff.dao.model;

/** How a ward ended: ran out of time, was destroyed, or the log does not say. */
public enum WardRemovalKind {
  EXPIRED,
  KILLED,
  UNKNOWN
}
