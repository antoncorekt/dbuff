package com.ako.dbuff.service.replay.analysis.behaviour;

import com.ako.dbuff.service.replay.analysis.behaviour.TeamClusters.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.behaviour.BehaviourTestData.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

class PostureClassifierTest {
  private final PostureClassifier classifier =
      new PostureClassifier(new TeamClusters(2500, 2, ZONES), 2500, 4, 2, 2);

  private static Map<Long, Point> at(int... xs) {
    Map<Long, Point> alive = new HashMap<>();
    for (int i = 0; i < xs.length; i++) {
      alive.put((long) i, new Point(xs[i], 0));
    }
    return alive;
  }

  @Test
  void classify_fiveTogetherAreGrouped() {
    assertThat(classifier.classify(at(0, 100, 200, 300, 400), Set.of())).contains(Posture.GROUPED);
  }

  @Test
  void classify_fourTogetherAndOneAwayAreStillGrouped() {
    assertThat(classifier.classify(at(0, 100, 200, 300, 9000), Set.of())).contains(Posture.GROUPED);
  }

  @Test
  void classify_twoHeroesAloneFarFromTheGroupAndEachOtherAreSplit() {
    assertThat(classifier.classify(at(0, 100, 200, -5000, 9000), Set.of())).contains(Posture.SPLIT);
  }

  @Test
  void classify_aGroupOfThreeAndAPairAreMixed() {
    assertThat(classifier.classify(at(0, 100, 200, 8000, 8500), Set.of())).contains(Posture.MIXED);
  }

  @Test
  void classify_threeAndTwoLoneHeroesAreSplit() {
    assertThat(classifier.classify(at(0, 100, 200, -5000, 9000), Set.of())).contains(Posture.SPLIT);
  }

  @Test
  void classify_threeAndAPairAreMixedNotSplit() {
    assertThat(classifier.classify(at(0, 100, 200, 8000, 8500), Set.of())).contains(Posture.MIXED);
  }

  @Test
  void classify_aTeamWithFewerLivingHeroesThanTheMinimumHasNoLabel() {
    PostureClassifier strict =
        new PostureClassifier(new TeamClusters(2500, 2, ZONES), 2500, 4, 2, 4);

    assertThat(strict.classify(at(0, 100, 200), Set.of())).isEmpty();
    assertThat(strict.classify(at(0, 9000), Set.of())).isEmpty();
    assertThat(strict.classify(at(0, 100, 200, 300), Set.of())).contains(Posture.GROUPED);
  }

  @Test
  void classify_oneHeroAloneIsNotASplit() {
    assertThat(classifier.classify(at(0, 100, 200, 400, -9000), Set.of()))
        .contains(Posture.GROUPED);
    assertThat(classifier.classify(at(0, 100, 200, 8000, 8500), Set.of())).contains(Posture.MIXED);
  }

  @Test
  void classify_threeLivingHeroesTogetherAreGroupedWhenTheMinimumAllows() {
    assertThat(classifier.classify(at(0, 100, 200), Set.of())).contains(Posture.GROUPED);
  }

  @Test
  void classify_heroesFarmingTheJungleAreNotAlone() {
    assertThat(classifier.classify(at(0, 100, 200, -5000, 9000), Set.of(3L, 4L)))
        .contains(Posture.MIXED);
  }

  @Test
  void classify_noLabelWithFewerThanTwoLivingHeroes() {
    assertThat(classifier.classify(at(0), Set.of())).isEqualTo(Optional.empty());
  }

  @Test
  void classify_everyoneApartIsSplit() {
    assertThat(classifier.classify(at(-9000, -3000, 3000, 9000, 15000), Set.of()))
        .contains(Posture.SPLIT);
  }
}
