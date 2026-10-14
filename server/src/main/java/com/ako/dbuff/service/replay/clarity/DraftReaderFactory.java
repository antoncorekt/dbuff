package com.ako.dbuff.service.replay.clarity;

/**
 * Makes the reader of a game mode's draft. It lives here, not on the mode profile, because the
 * reader works on Clarity types that nothing outside this package may see; a mode with a draft to
 * read adds one factory bean and the parser needs no change.
 *
 * @param <T> the facts the readers produce
 */
interface DraftReaderFactory<T> {
  long gameModeId();

  /** A reader for one parse. */
  DraftReader<T> create();
}
