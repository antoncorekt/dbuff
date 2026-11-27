package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({DefenseItemAnalyzer.class, DefenseItemsFile.class})
class DefenseItemAnalyzerTest {

  private static final long MATCH = 21L;

  @Autowired private DefenseItemAnalyzer analyzer;
  @Autowired private ReplayStateChangeRepo stateRepo;
  @Autowired private TeamDefenseItemRepo itemRepo;

  @Test
  void firstGainIsStoredWithTeamKindAndSecond() {
    change(0, 600.7, StateChangeKind.ITEM_ADDED, "item_pipe");
    change(130, 900, StateChangeKind.ITEM_ADDED, "item_heart");

    analyzer.analyze(MATCH);

    List<TeamDefenseItemDomain> rows = itemRepo.findByMatchId(MATCH);
    assertThat(rows)
        .extracting("team", "playerSlot", "item", "kind", "atSecond", "analysisVersion")
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("RADIANT", 0L, "pipe", "MAGIC", 600, 1),
            org.assertj.core.groups.Tuple.tuple("DIRE", 130L, "heart", "HP", 900, 1));
  }

  @Test
  void aReAddWithinAMinuteOfLosingItIsNotStored() {
    change(0, 600, StateChangeKind.ITEM_ADDED, "item_pipe");
    change(0, 700, StateChangeKind.ITEM_REMOVED, "item_pipe");
    change(0, 750, StateChangeKind.ITEM_ADDED, "item_pipe");

    analyzer.analyze(MATCH);

    assertThat(itemRepo.findByMatchId(MATCH)).extracting("atSecond").containsExactly(600);
  }

  @Test
  void aReBuyAfterNinetySecondsIsStoredAgain() {
    change(0, 600, StateChangeKind.ITEM_ADDED, "item_pipe");
    change(0, 700, StateChangeKind.ITEM_REMOVED, "item_pipe");
    change(0, 790, StateChangeKind.ITEM_ADDED, "item_pipe");

    analyzer.analyze(MATCH);

    assertThat(itemRepo.findByMatchId(MATCH))
        .extracting("atSecond")
        .containsExactlyInAnyOrder(600, 790);
  }

  @Test
  void itemsOutsideTheCatalogAreIgnored_andARunReplacesTheLast() {
    change(0, 300, StateChangeKind.ITEM_ADDED, "item_blink");
    change(0, 400, StateChangeKind.ITEM_ADDED, "item_manta");

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(itemRepo.findByMatchId(MATCH)).extracting("item").containsExactly("manta");
  }

  private void change(long slot, double seconds, StateChangeKind kind, String name) {
    stateRepo.save(
        ReplayStateChangeDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .gameSeconds(seconds)
            .kind(kind.name())
            .name(name)
            .slotIndex(0)
            .build());
  }
}
