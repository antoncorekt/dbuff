package com.ako.dbuff.service.replay.file;

import com.ako.dbuff.service.replay.ReplayException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongFunction;

/**
 * Copies a stream while counting the bytes itself, so a wrong or missing length cannot be trusted.
 */
final class LimitedCopier {
  private static final int BUFFER_BYTES = 64 * 1024;

  private LimitedCopier() {}

  /**
   * @param overLimit builds the exception to throw when more than {@code limitBytes} arrive
   * @return the number of bytes copied
   */
  static long copy(
      InputStream in, OutputStream out, long limitBytes, LongFunction<ReplayException> overLimit)
      throws IOException {
    long total = 0;
    byte[] buffer = new byte[BUFFER_BYTES];
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > limitBytes) {
        throw overLimit.apply(limitBytes);
      }
      out.write(buffer, 0, read);
    }
    return total;
  }
}
