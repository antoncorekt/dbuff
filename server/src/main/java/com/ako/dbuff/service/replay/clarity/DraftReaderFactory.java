package com.ako.dbuff.service.replay.clarity;

/**
 * Makes the reader of a game mode's draft. It lives here, not on the mode profile, because the
 * reader works on Clarity types that nothing outside this package may see. A mode with a draft to
 * read that has an existing part (skills, heroes) adds one factory bean. A new kind of draft part
 * costs a factory bean, a {@code ReplayFacts} field and one more reader slot in {@code
 * ClarityReplayParser}; that is a known limit (open for extension only through the parser), not
 * worth a reader list until a third part exists.
 *
 * @param <T> the facts the readers produce
 */
interface DraftReaderFactory<T> {
  long gameModeId();

  /** A reader for one parse. */
  DraftReader<T> create();
}
