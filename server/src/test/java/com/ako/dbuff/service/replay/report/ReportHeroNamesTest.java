package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.icon.TestIcons;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportHeroNamesTest {

  @Test
  void bySlot_prefersTheStoredHeroNameAndFallsBackToTheEntityName() {
    PlayerGameStatisticRepo stats = mock(PlayerGameStatisticRepo.class);
    ReplayPlayerRepo replay = mock(ReplayPlayerRepo.class);
    HeroKits kits = mock(HeroKits.class);
    when(replay.findByMatchId(1L))
        .thenReturn(
            List.of(
                ReplayPlayerDomain.builder().playerSlot(0L).heroEntityName("Furion").build(),
                ReplayPlayerDomain.builder()
                    .playerSlot(1L)
                    .heroEntityName("PhantomAssassin")
                    .build()));
    when(stats.findAllByMatchId(1L))
        .thenReturn(
            List.of(PlayerMatchStatisticDomain.builder().playerSlot(0L).heroId(53L).build()));
    when(kits.hero(53L))
        .thenReturn(
            Optional.of(new HeroConstant("53", "npc_dota_hero_furion", "Nature's Prophet")));

    Map<Long, String> names = new ReportHeroNames(stats, replay, kits, TestIcons.off()).bySlot(1L);

    assertThat(names).containsEntry(0L, "Nature's Prophet").containsEntry(1L, "Phantom Assassin");
  }
}
