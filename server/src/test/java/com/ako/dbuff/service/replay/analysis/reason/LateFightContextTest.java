package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LateFightContextTest {
  private static FightVisionDomain row(long id, String team) {
    return FightVisionDomain.builder().id(id).team(team).build();
  }

  @Test
  void severalRowsOfOneTeamGiveTheLargestIdWhateverTheOrder() {
    LateFightContext forward =
        new LateFightContext(
            null, List.of(row(1, "DIRE"), row(5, "DIRE"), row(3, "DIRE")), List.of());
    LateFightContext backward =
        new LateFightContext(null, List.of(row(5, "DIRE"), row(1, "DIRE")), List.of());

    assertThat(forward.vision(TeamSide.DIRE))
        .get()
        .extracting(FightVisionDomain::getId)
        .isEqualTo(5L);
    assertThat(backward.vision(TeamSide.DIRE))
        .get()
        .extracting(FightVisionDomain::getId)
        .isEqualTo(5L);
    assertThat(forward.vision(TeamSide.RADIANT)).isEmpty();
  }
}
