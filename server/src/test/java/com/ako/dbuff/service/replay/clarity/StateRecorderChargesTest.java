package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.StateChange;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.stringtables.StringTables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateRecorderChargesTest {

  private static final String STICK = "item_magic_stick";
  private static final int HANDLE = 7;

  private final Entities entities = mock(Entities.class);
  private final StringTables stringTables = mock(StringTables.class);
  private final Map<String, Object> heroProperties = new HashMap<>();
  private final Map<String, Object> stickProperties = new HashMap<>();
  private final StateRecorder recorder =
      new StateRecorder(entities, stringTables, Set.of(STICK, "item_magic_wand"));

  StateRecorderChargesTest() {
    StringTable names = mock(StringTable.class);
    when(names.getNameByIndex(0)).thenReturn(STICK);
    when(stringTables.forName("EntityNames")).thenReturn(names);
    Entity stick = entity(stickProperties);
    stickProperties.put("m_pEntity.m_nameStringTableIndex", 0);
    when(entities.getByHandle(HANDLE)).thenReturn(stick);
  }

  @Test
  void aChangedCountIsEmittedOnceAndAnUnchangedOneIsNot() {
    sample(0, true, 0);
    sample(150, true, 2);
    sample(300, true, 2);
    sample(450, true, 5);

    assertThat(charges()).extracting(StateChange::charges).containsExactly(2, 5);
  }

  @Test
  void anItemThatLeavesTheInventoryForgetsItsCount() {
    sample(0, true, 3);
    sample(150, false, 0);
    sample(300, true, 3);

    assertThat(charges()).extracting(StateChange::charges).containsExactly(3, 3);
  }

  @Test
  void anItemOutsideTheWhitelistIsNotTracked() {
    StateRecorder other = new StateRecorder(entities, stringTables, Set.of("item_magic_wand"));
    heroProperties.put("m_hItems.0000", HANDLE);
    stickProperties.put("m_iCurrentCharges", 4);

    other.sampled(0, List.of(new Controllers.View(0, 2, 1L, hero())));

    assertThat(other.result(0).changes()).noneMatch(c -> c.kind() == StateChangeKind.ITEM_CHARGES);
  }

  private void sample(long tick, boolean held, int charges) {
    if (held) {
      heroProperties.put("m_hItems.0000", HANDLE);
    } else {
      heroProperties.remove("m_hItems.0000");
    }
    stickProperties.put("m_iCurrentCharges", charges);
    recorder.sampled(tick, List.of(new Controllers.View(0, 2, 1L, hero())));
  }

  private List<StateChange> charges() {
    return recorder.result(0).changes().stream()
        .filter(c -> c.kind() == StateChangeKind.ITEM_CHARGES)
        .toList();
  }

  private Entity hero() {
    return entity(heroProperties);
  }

  private static Entity entity(Map<String, Object> properties) {
    Entity entity = mock(Entity.class);
    when(entity.hasProperty(anyString()))
        .thenAnswer(i -> properties.containsKey(i.<String>getArgument(0)));
    when(entity.getProperty(anyString())).thenAnswer(i -> properties.get(i.<String>getArgument(0)));
    return entity;
  }
}
