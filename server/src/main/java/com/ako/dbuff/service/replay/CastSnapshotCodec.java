package com.ako.dbuff.service.replay;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Packs the cast snapshots into one compressed blob: a table of ability names, then frames. */
public final class CastSnapshotCodec {
  /** Bumped whenever the layout or the meaning of a field changes. */
  public static final int VERSION = 1;

  private static final double CENTISECONDS = 100.0;

  private CastSnapshotCodec() {}

  public static byte[] encode(List<CastSnapshot> snapshots) {
    Map<String, Integer> names = new HashMap<>();
    List<String> table = new ArrayList<>();
    snapshots.forEach(
        s ->
            names.computeIfAbsent(
                s.ability(),
                n -> {
                  table.add(n);
                  return table.size() - 1;
                }));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new ZstdOutputStream(bytes))) {
      out.writeInt(VERSION);
      out.writeInt(table.size());
      for (String name : table) {
        out.writeUTF(name);
      }
      out.writeInt(snapshots.size());
      for (CastSnapshot s : snapshots) {
        out.writeByte(names.get(s.ability()));
        out.writeInt((int) Math.round(s.castSecond() * CENTISECONDS));
        out.writeInt((int) Math.round(s.second() * CENTISECONDS));
        out.writeByte(s.casterId());
        out.writeShort(s.facing());
        out.writeByte(s.heroes().size());
        for (CastSnapshot.HeroPoint h : s.heroes()) {
          checkRange(h);
          out.writeByte(h.playerId());
          out.writeShort(h.x());
          out.writeShort(h.y());
          out.writeBoolean(h.alive());
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static void checkRange(CastSnapshot.HeroPoint h) {
    if (h.playerId() < 0
        || h.playerId() > Byte.MAX_VALUE
        || h.x() < Short.MIN_VALUE
        || h.x() > Short.MAX_VALUE
        || h.y() < Short.MIN_VALUE
        || h.y() > Short.MAX_VALUE) {
      throw new IllegalArgumentException("Hero point does not fit the format: " + h);
    }
  }

  public static List<CastSnapshot> decode(byte[] blob) {
    try (DataInputStream in =
        new DataInputStream(new ZstdInputStream(new ByteArrayInputStream(blob)))) {
      int version = in.readInt();
      if (version != VERSION) {
        throw new IllegalArgumentException("Cast snapshot blob has version " + version);
      }
      String[] table = new String[in.readInt()];
      for (int i = 0; i < table.length; i++) {
        table[i] = in.readUTF();
      }
      int count = in.readInt();
      List<CastSnapshot> snapshots = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String ability = table[in.readUnsignedByte()];
        double castSecond = in.readInt() / CENTISECONDS;
        double second = in.readInt() / CENTISECONDS;
        int casterId = in.readUnsignedByte();
        int facing = in.readShort();
        int heroCount = in.readUnsignedByte();
        List<CastSnapshot.HeroPoint> heroes = new ArrayList<>(heroCount);
        for (int h = 0; h < heroCount; h++) {
          heroes.add(
              new CastSnapshot.HeroPoint(
                  in.readUnsignedByte(), in.readShort(), in.readShort(), in.readBoolean()));
        }
        snapshots.add(new CastSnapshot(ability, castSecond, second, casterId, facing, heroes));
      }
      return snapshots;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
