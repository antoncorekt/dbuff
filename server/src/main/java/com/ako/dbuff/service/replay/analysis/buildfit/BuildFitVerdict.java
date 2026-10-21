package com.ako.dbuff.service.replay.analysis.buildfit;

/** How firmly a build's lacks are backed by the match. */
public enum BuildFitVerdict {
  /** Nothing the role needs is missing. */
  FIT,
  /** Something is missing, but the match backs none of it. */
  GAPS_NOT_SHOWN,
  /** At least one missing function is backed by the match. */
  GAP
}
