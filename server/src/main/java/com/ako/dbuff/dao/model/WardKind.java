package com.ako.dbuff.dao.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The two ward items, with the seconds a ward lives when nobody destroys it. */
@Getter
@RequiredArgsConstructor
public enum WardKind {
  OBSERVER(360),
  SENTRY(420);

  private final long lifetimeSeconds;
}
