package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.GoldReason;
import com.ako.dbuff.service.replay.XpReason;
import org.junit.jupiter.api.Test;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.wire.dota.common.proto.DOTACombatLog.DOTA_COMBATLOG_TYPES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatLogRecorderTest {

  @Test
  void record_keepsTheGoldReasonForGoldAndTheXpReasonForXp_andTheStunDurationForTheRest() {
    CombatLogRecorder recorder = new CombatLogRecorder();
    recorder.record(entry(DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_GOLD, 14, 2, 0.5f));
    recorder.record(entry(DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_XP, 14, 2, 0.5f));
    recorder.record(entry(DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH, 14, 2, 0.5f));

    var events = recorder.result(0).events();

    assertThat(events).hasSize(3);
    assertThat(events.get(0).goldReason()).isEqualTo(GoldReason.NEUTRAL_KILL);
    assertThat(events.get(1).xpReason()).isEqualTo(XpReason.CREEP_KILL);
    assertThat(events.get(2).aux()).as("a stun duration in hundredths").isEqualTo(50);
  }

  @Test
  void record_keepsAHealWithItsInflictorTargetAndValue_whenAHeroIsInvolved() {
    CombatLogRecorder recorder = new CombatLogRecorder();
    CombatLogEntry heal = entry(DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_HEAL, 0, 0, 0f);
    when(heal.getInflictorName()).thenReturn("item_mekansm");
    when(heal.getValue()).thenReturn(250);
    CombatLogEntry creepHeal = entry(DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_HEAL, 0, 0, 0f);
    when(creepHeal.isTargetHero()).thenReturn(false);
    recorder.record(heal);
    recorder.record(creepHeal);

    var events = recorder.result(0).events();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().kind()).isEqualTo(CombatEventKind.HEAL);
    assertThat(events.getFirst().inflictor()).isEqualTo("item_mekansm");
    assertThat(events.getFirst().target()).isEqualTo("npc_dota_hero_luna");
    assertThat(events.getFirst().value()).isEqualTo(250);
  }

  private static CombatLogEntry entry(
      DOTA_COMBATLOG_TYPES type, int goldReason, int xpReason, float stun) {
    CombatLogEntry entry = mock(CombatLogEntry.class);
    when(entry.getType()).thenReturn(type);
    when(entry.getGoldReason()).thenReturn(goldReason);
    when(entry.getXpReason()).thenReturn(xpReason);
    when(entry.isTargetHero()).thenReturn(true);
    when(entry.getStunDuration()).thenReturn(stun);
    when(entry.getTargetName()).thenReturn("npc_dota_hero_luna");
    when(entry.getAttackerName()).thenReturn("dota_unknown");
    when(entry.getInflictorName()).thenReturn("dota_unknown");
    return entry;
  }
}
