package com.ako.dbuff.service.replay;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Blobs as an older application version wrote them. */
public final class CombatLogBlobs {
  private CombatLogBlobs() {}

  /** An empty version 1 blob: GOLD and XP events in it carry no reason. */
  public static byte[] versionOne() {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(raw)) {
      out.writeInt(1);
      out.writeInt(0);
      out.writeInt(0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Zstd.compress(raw.toByteArray());
  }
}
