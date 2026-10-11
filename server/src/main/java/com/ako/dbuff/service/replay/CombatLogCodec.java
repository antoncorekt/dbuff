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

/**
 * Packs the combat-log events into one compressed blob: a table of the distinct names, then the
 * events with names as indexes. A game's 20 to 30 thousand events take a few hundred kilobytes.
 */
public final class CombatLogCodec {
  /** Bumped whenever the layout changes, so an old blob is never read as a new one. */
  public static final int VERSION = 1;

  private static final int MAX_NAMES = 65_535;
  private static final int MAX_ASSIST_MASK = 0xFFFF;
  private static final double CENTISECONDS = 100.0;
  private static final CombatEventKind[] KINDS = CombatEventKind.values();

  private CombatLogCodec() {}

  public static byte[] encode(List<CombatEvent> events) {
    Map<String, Integer> names = new HashMap<>();
    List<String> table = new ArrayList<>();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new ZstdOutputStream(bytes))) {
      for (CombatEvent e : events) {
        for (String name : new String[] {e.attacker(), e.target(), e.inflictor()}) {
          names.computeIfAbsent(
              name,
              n -> {
                table.add(n);
                return table.size() - 1;
              });
        }
      }
      if (table.size() > MAX_NAMES) {
        throw new IllegalArgumentException(
            "Combat log has " + table.size() + " distinct names, the format holds " + MAX_NAMES);
      }
      out.writeInt(VERSION);
      out.writeInt(table.size());
      for (String name : table) {
        out.writeUTF(name);
      }
      out.writeInt(events.size());
      for (CombatEvent e : events) {
        checkRange(e);
        out.writeByte(e.kind().ordinal());
        out.writeInt((int) Math.round(e.gameSecond() * CENTISECONDS));
        out.writeShort(names.get(e.attacker()));
        out.writeShort(names.get(e.target()));
        out.writeShort(names.get(e.inflictor()));
        out.writeByte(e.flags());
        out.writeInt(e.value());
        out.writeInt(e.health());
        out.writeInt(e.aux());
        out.writeInt(e.aux2());
        out.writeShort(e.x());
        out.writeShort(e.y());
        out.writeShort(e.assistMask());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  /** What the layout stores in 8 or 16 bits must fit, or it would be corrupted silently. */
  private static void checkRange(CombatEvent e) {
    if (e.flags() < 0
        || e.flags() > Byte.MAX_VALUE * 2 + 1
        || e.assistMask() < 0
        || e.assistMask() > MAX_ASSIST_MASK
        || e.x() < Short.MIN_VALUE
        || e.x() > Short.MAX_VALUE
        || e.y() < Short.MIN_VALUE
        || e.y() > Short.MAX_VALUE) {
      throw new IllegalArgumentException("Combat event does not fit the format: " + e);
    }
  }

  public static List<CombatEvent> decode(byte[] blob) {
    try (DataInputStream in =
        new DataInputStream(new ZstdInputStream(new ByteArrayInputStream(blob)))) {
      int version = in.readInt();
      if (version != VERSION) {
        throw new IllegalArgumentException("Combat log blob has version " + version);
      }
      String[] table = new String[in.readInt()];
      for (int i = 0; i < table.length; i++) {
        table[i] = in.readUTF();
      }
      int count = in.readInt();
      List<CombatEvent> events = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        CombatEventKind kind = KINDS[in.readUnsignedByte()];
        double second = in.readInt() / CENTISECONDS;
        String attacker = table[in.readUnsignedShort()];
        String target = table[in.readUnsignedShort()];
        String inflictor = table[in.readUnsignedShort()];
        events.add(
            new CombatEvent(
                kind,
                second,
                attacker,
                target,
                inflictor,
                in.readUnsignedByte(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readShort(),
                in.readShort(),
                in.readUnsignedShort()));
      }
      return events;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
