# Statistics Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add hero filtering, average-use aggregation, and conjunctive combo queries to the item/ability/player statistics repositories, and fix the silent name-resolution bug they currently share.

**Architecture:** Pure data-layer work on existing Criteria API repositories in `com.ako.dbuff.dao.repo` and their services in `com.ako.dbuff.service.ranking`. No Discord code. Every change is independently useful — the existing REST endpoints gain the same capabilities and the same bug fix.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA Criteria API with generated metamodel (`ItemDomain_`, `PlayerMatchStatisticDomain_`, …), Lombok, JUnit 5 + AssertJ, H2 in PostgreSQL mode via `@DataJpaTest`.

**Spec:** `docs/superpowers/specs/2026-08-19-discord-friendly-commands-design.md`

**This is plan 1 of 2.** Plan 2 (`2026-08-19-discord-command-layer.md`) builds the Discord command surface on top of this and depends on Tasks 1–4 and 6–10 here.

---

## Conventions

Confirmed from the existing codebase — follow these exactly:

- Services: `@Slf4j @Service @RequiredArgsConstructor`, `final` fields, `@Transactional(readOnly = true)` on reads.
- Repositories with Criteria queries are `@Repository` classes with `@PersistenceContext private EntityManager entityManager;` — **not** interfaces.
- Response models are Lombok `@Data @Builder` records-in-name-only classes in `com.ako.dbuff.resources.model`.
- Repository tests: `@DataJpaTest`, `@Import(TheRepository.class)`, `@ActiveProfiles("test")`, `@Autowired EntityManager`, build fixtures with `entityManager.persist(...)`.
- Static imports for AssertJ: `import static org.assertj.core.api.Assertions.assertThat;`
- Run one test class: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.ItemRankingRepositoryTest"`
- Run everything: `./gradlew test`
- **Always** `./gradlew spotlessApply` before committing — Spotless enforces Google Java Format and CI fails on violations.

### Constant map shapes (verified)

| Accessor | Key | Value fields |
|---|---|---|
| `getItemConstantMap()` | item short name, e.g. `blink` | `ItemConstant{Long id, String dname, Long cost, …}` where `dname` is the display name, e.g. `Blink Dagger` |
| `getHeroConstantMap()` | hero ID as `String` | `HeroConstant{String id, String name, String localized_name}` |
| `getAbilityConstantMap()` | ability ID as `String` | `AbilityIdsConstant{Long id, String name}` |

---

## Task 1: `StatsPeriod` enum

Resolves a preset period choice into a `(startDate, endDate)` pair. Needed by every
`/stats` subcommand in plan 2, and useful on its own for the REST layer.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/ranking/StatsPeriod.java`
- Test: `server/src/test/java/com/ako/dbuff/service/ranking/StatsPeriodTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/service/ranking/StatsPeriodTest.java`:

```java
package com.ako.dbuff.service.ranking;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsPeriodTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

  @Test
  void last7Days_startsSevenDaysBeforeToday() {
    StatsPeriod.Range range = StatsPeriod.LAST_7_DAYS.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(range.endDate()).isEqualTo(TODAY);
  }

  @Test
  void last30Days_startsThirtyDaysBeforeToday() {
    StatsPeriod.Range range = StatsPeriod.LAST_30_DAYS.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(range.endDate()).isEqualTo(TODAY);
  }

  @Test
  void allTime_hasNullStartDate() {
    StatsPeriod.Range range = StatsPeriod.ALL_TIME.resolve(TODAY, null);

    assertThat(range.startDate()).isNull();
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isFalse();
  }

  @Test
  void currentPatch_usesSuppliedPatchStartDate() {
    StatsPeriod.Range range =
        StatsPeriod.CURRENT_PATCH.resolve(TODAY, LocalDate.of(2026, 8, 1));

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isFalse();
  }

  @Test
  void currentPatch_withoutPatchDate_fallsBackTo30DaysAndFlagsIt() {
    StatsPeriod.Range range = StatsPeriod.CURRENT_PATCH.resolve(TODAY, null);

    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(range.endDate()).isEqualTo(TODAY);
    assertThat(range.fellBack()).isTrue();
  }

  @Test
  void fromChoiceValue_isCaseInsensitive() {
    assertThat(StatsPeriod.fromChoiceValue("last_7_days")).isEqualTo(StatsPeriod.LAST_7_DAYS);
    assertThat(StatsPeriod.fromChoiceValue("LAST_7_DAYS")).isEqualTo(StatsPeriod.LAST_7_DAYS);
  }

  @Test
  void fromChoiceValue_unknownOrNull_defaultsTo30Days() {
    assertThat(StatsPeriod.fromChoiceValue(null)).isEqualTo(StatsPeriod.LAST_30_DAYS);
    assertThat(StatsPeriod.fromChoiceValue("nonsense")).isEqualTo(StatsPeriod.LAST_30_DAYS);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.ranking.StatsPeriodTest"`

Expected: **compilation failure** — `cannot find symbol: class StatsPeriod`.

- [ ] **Step 3: Write the implementation**

Create `server/src/main/java/com/ako/dbuff/service/ranking/StatsPeriod.java`:

```java
package com.ako.dbuff.service.ranking;

import java.time.LocalDate;

/**
 * Preset time ranges for statistics queries.
 *
 * <p>{@code resolve} takes today's date and the current patch's start date as parameters rather than
 * reading a clock or a service, so the enum stays trivially testable. Callers supply them.
 */
public enum StatsPeriod {
  LAST_7_DAYS("Last 7 days"),
  LAST_30_DAYS("Last 30 days"),
  CURRENT_PATCH("Current patch"),
  ALL_TIME("All time");

  /** Fallback when a caller supplies no period, or an unrecognised one. */
  public static final StatsPeriod DEFAULT = LAST_30_DAYS;

  private static final int DEFAULT_DAYS = 30;

  private final String displayName;

  StatsPeriod(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  /** The value submitted by a Discord choice option, e.g. {@code last_7_days}. */
  public String getChoiceValue() {
    return name().toLowerCase();
  }

  /**
   * A resolved date range. {@code startDate} is null for {@link #ALL_TIME}, which the repositories
   * interpret as "no lower bound". {@code fellBack} is true when {@link #CURRENT_PATCH} could not
   * determine a patch start date and silently degraded to 30 days — callers should say so rather
   * than presenting the result as patch-scoped.
   */
  public record Range(LocalDate startDate, LocalDate endDate, boolean fellBack) {}

  /**
   * @param today the end of the range
   * @param patchStartDate start date of the current patch, or null if unknown
   */
  public Range resolve(LocalDate today, LocalDate patchStartDate) {
    return switch (this) {
      case LAST_7_DAYS -> new Range(today.minusDays(7), today, false);
      case LAST_30_DAYS -> new Range(today.minusDays(DEFAULT_DAYS), today, false);
      case ALL_TIME -> new Range(null, today, false);
      case CURRENT_PATCH ->
          patchStartDate != null
              ? new Range(patchStartDate, today, false)
              : new Range(today.minusDays(DEFAULT_DAYS), today, true);
    };
  }

  /** Parses a Discord choice value, defaulting to {@link #DEFAULT} for null or unknown input. */
  public static StatsPeriod fromChoiceValue(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    for (StatsPeriod period : values()) {
      if (period.name().equalsIgnoreCase(value)) {
        return period;
      }
    }
    return DEFAULT;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.ranking.StatsPeriodTest"`

Expected: **PASS**, 7 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/ranking/StatsPeriod.java \
        server/src/test/java/com/ako/dbuff/service/ranking/StatsPeriodTest.java
git commit -m "feat: add StatsPeriod enum for preset statistics date ranges"
```

---

## Task 2: `NameResolution` and `ConstantNameResolver`

Fixes a **pre-existing bug**. Today `ItemRankingService.convertDnamesToIds` and
`AbilityRankingService.convertNamesToIds` log a warning for an unknown name and
then `filter(Objects::nonNull)` it away. If *every* name is unknown they return
`null`, and the repositories treat a null ID set as "no filter" — so a query for
two specific items that both fail to resolve silently returns an unfiltered top-N
ranking instead of an error.

This task builds the replacement. Task 3 wires it in.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/constant/NameResolution.java`
- Create: `server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java`
- Test: `server/src/test/java/com/ako/dbuff/service/constant/ConstantNameResolverTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/service/constant/ConstantNameResolverTest.java`:

```java
package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantNameResolverTest {

  private ConstantsManagers constantsManagers;
  private ConstantNameResolver resolver;

  @BeforeEach
  void setUp() {
    constantsManagers = Mockito.mock(ConstantsManagers.class);

    Mockito.when(constantsManagers.getItemConstantMap())
        .thenReturn(
            Map.of(
                "blink", ItemConstant.builder().id(1L).dname("Blink Dagger").build(),
                "black_king_bar", ItemConstant.builder().id(2L).dname("Black King Bar").build()));

    Mockito.when(constantsManagers.getHeroConstantMap())
        .thenReturn(
            Map.of(
                "1", new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage"),
                "74", new HeroConstant("74", "npc_dota_hero_invoker", "Invoker")));

    Mockito.when(constantsManagers.getAbilityConstantMap())
        .thenReturn(
            Map.of(
                "5001", AbilityIdsConstant.builder().id(5001L).name("invoker_quas").build(),
                "5002", AbilityIdsConstant.builder().id(5002L).name("invoker_wex").build()));

    resolver = new ConstantNameResolver(constantsManagers);
  }

  @Test
  void resolveItems_matchesShortNameAndDisplayName() {
    NameResolution result = resolver.resolveItems(Set.of("blink", "Black King Bar"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(result.unresolvedNames()).isEmpty();
    assertThat(result.hasUnresolved()).isFalse();
  }

  @Test
  void resolveItems_isCaseInsensitive() {
    NameResolution result = resolver.resolveItems(Set.of("BLINK", "black king bar"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void resolveItems_reportsUnknownNamesInsteadOfDroppingThem() {
    NameResolution result = resolver.resolveItems(Set.of("blink", "Sheepstick Of Doom"));

    assertThat(result.resolvedIds()).containsExactly(1L);
    assertThat(result.unresolvedNames()).containsExactly("Sheepstick Of Doom");
    assertThat(result.hasUnresolved()).isTrue();
  }

  @Test
  void resolveItems_allUnknown_reportsThemAndResolvesNothing() {
    NameResolution result = resolver.resolveItems(Set.of("nonsense", "garbage"));

    assertThat(result.resolvedIds()).isEmpty();
    assertThat(result.unresolvedNames()).containsExactlyInAnyOrder("nonsense", "garbage");
    assertThat(result.hasUnresolved()).isTrue();
  }

  @Test
  void resolveItems_nullOrEmptyInput_isEmptyWithNoComplaints() {
    assertThat(resolver.resolveItems(null).resolvedIds()).isEmpty();
    assertThat(resolver.resolveItems(null).hasUnresolved()).isFalse();
    assertThat(resolver.resolveItems(Set.of()).resolvedIds()).isEmpty();
    assertThat(resolver.resolveItems(Set.of()).hasUnresolved()).isFalse();
  }

  @Test
  void resolveHeroes_matchesInternalAndLocalizedName() {
    assertThat(resolver.resolveHeroes(Set.of("Invoker")).resolvedIds()).containsExactly(74L);
    assertThat(resolver.resolveHeroes(Set.of("npc_dota_hero_antimage")).resolvedIds())
        .containsExactly(1L);
  }

  @Test
  void resolveHeroes_unknownHero_isReported() {
    NameResolution result = resolver.resolveHeroes(Set.of("Not A Hero"));

    assertThat(result.resolvedIds()).isEmpty();
    assertThat(result.unresolvedNames()).containsExactly("Not A Hero");
  }

  @Test
  void resolveAbilities_matchesInternalName() {
    NameResolution result = resolver.resolveAbilities(Set.of("invoker_quas", "INVOKER_WEX"));

    assertThat(result.resolvedIds()).containsExactlyInAnyOrder(5001L, 5002L);
    assertThat(result.unresolvedNames()).isEmpty();
  }

  @Test
  void resolveAbilities_unknownAbility_isReported() {
    NameResolution result = resolver.resolveAbilities(Set.of("invoker_quas", "made_up_spell"));

    assertThat(result.resolvedIds()).containsExactly(5001L);
    assertThat(result.unresolvedNames()).containsExactly("made_up_spell");
  }

  @Test
  void suggestItem_returnsClosestNameByEditDistance() {
    assertThat(resolver.suggestItem("blnk")).contains("Blink Dagger");
    assertThat(resolver.suggestItem("Black King Bra")).contains("Black King Bar");
  }

  @Test
  void suggestItem_whollyUnrelatedInput_returnsEmpty() {
    assertThat(resolver.suggestItem("zzzzzzzzzzzzzzzz")).isEmpty();
  }

  @Test
  void suggestHero_returnsClosestLocalizedName() {
    assertThat(resolver.suggestHero("Invokr")).contains("Invoker");
  }

  @Test
  void suggestAbility_returnsClosestInternalName() {
    assertThat(resolver.suggestAbility("invoker_qua")).contains("invoker_quas");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.constant.ConstantNameResolverTest"`

Expected: **compilation failure** — `cannot find symbol: class ConstantNameResolver`.

- [ ] **Step 3: Write `NameResolution`**

Create `server/src/main/java/com/ako/dbuff/service/constant/NameResolution.java`:

```java
package com.ako.dbuff.service.constant;

import java.util.Set;

/**
 * The outcome of resolving user-supplied constant names to numeric IDs.
 *
 * <p>Deliberately returns unresolved names alongside the resolved IDs so that callers must decide
 * what to do about them. The previous behaviour silently discarded unknown names, which turned a
 * filtered query into an unfiltered one and reported the wrong statistics without any error.
 *
 * @param resolvedIds IDs successfully resolved; never null, possibly empty
 * @param unresolvedNames names that matched no constant; never null, possibly empty
 */
public record NameResolution(Set<Long> resolvedIds, Set<String> unresolvedNames) {

  public static NameResolution empty() {
    return new NameResolution(Set.of(), Set.of());
  }

  public boolean hasUnresolved() {
    return !unresolvedNames.isEmpty();
  }

  /**
   * The resolved IDs, or null when none were requested — matching the convention the ranking
   * repositories use, where a null filter set means "no filter".
   *
   * <p>Only safe to call after checking {@link #hasUnresolved()}; otherwise an all-unknown input
   * would produce null and silently widen the query.
   */
  public Set<Long> idsOrNullIfEmpty() {
    return resolvedIds.isEmpty() ? null : resolvedIds;
  }
}
```

- [ ] **Step 4: Write `ConstantNameResolver`**

Create `server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java`:

```java
package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves user-supplied item, hero, and ability names to numeric IDs, reporting anything it could
 * not resolve rather than discarding it.
 *
 * <p>Matching is case-insensitive and accepts either the internal name or the display name, because
 * Discord autocomplete submits internal names while a user typing freehand will use display names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstantNameResolver {

  /**
   * Maximum edit distance for a suggestion to be offered. Beyond this the "did you mean" is noise
   * rather than help.
   */
  private static final int MAX_SUGGESTION_DISTANCE = 4;

  private final ConstantsManagers constantsManagers;

  public NameResolution resolveItems(Set<String> names) {
    return resolve(names, this::itemIdFor);
  }

  public NameResolution resolveHeroes(Set<String> names) {
    return resolve(names, this::heroIdFor);
  }

  public NameResolution resolveAbilities(Set<String> names) {
    return resolve(names, this::abilityIdFor);
  }

  public Optional<String> suggestItem(String unknown) {
    return closest(unknown, itemDisplayNames());
  }

  public Optional<String> suggestHero(String unknown) {
    return closest(unknown, heroDisplayNames());
  }

  public Optional<String> suggestAbility(String unknown) {
    return closest(unknown, abilityNames());
  }

  private NameResolution resolve(Set<String> names, Function<String, Optional<Long>> lookup) {
    if (names == null || names.isEmpty()) {
      return NameResolution.empty();
    }

    Set<Long> resolved = new LinkedHashSet<>();
    Set<String> unresolved = new LinkedHashSet<>();

    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      lookup.apply(name.trim()).ifPresentOrElse(resolved::add, () -> unresolved.add(name.trim()));
    }

    if (!unresolved.isEmpty()) {
      log.debug("Unresolved constant names: {}", unresolved);
    }
    return new NameResolution(resolved, unresolved);
  }

  private Optional<Long> itemIdFor(String name) {
    Map<String, ItemConstant> items = constantsManagers.getItemConstantMap();

    ItemConstant byKey = items.get(name.toLowerCase());
    if (byKey != null) {
      return Optional.ofNullable(byKey.getId());
    }
    return items.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().equalsIgnoreCase(name)
                    || (entry.getValue().getDname() != null
                        && entry.getValue().getDname().equalsIgnoreCase(name)))
        .map(entry -> entry.getValue().getId())
        .filter(java.util.Objects::nonNull)
        .findFirst();
  }

  private Optional<Long> heroIdFor(String name) {
    return constantsManagers.getHeroConstantMap().values().stream()
        .filter(
            hero ->
                (hero.getName() != null && hero.getName().equalsIgnoreCase(name))
                    || (hero.getLocalized_name() != null
                        && hero.getLocalized_name().equalsIgnoreCase(name)))
        .map(HeroConstant::getId)
        .filter(java.util.Objects::nonNull)
        .map(Long::valueOf)
        .findFirst();
  }

  private Optional<Long> abilityIdFor(String name) {
    return constantsManagers.getAbilityConstantMap().values().stream()
        .filter(ability -> ability.getName() != null && ability.getName().equalsIgnoreCase(name))
        .map(AbilityIdsConstant::getId)
        .filter(java.util.Objects::nonNull)
        .findFirst();
  }

  private Set<String> itemDisplayNames() {
    Set<String> names = new LinkedHashSet<>();
    constantsManagers
        .getItemConstantMap()
        .forEach(
            (key, item) -> {
              if (item.getDname() != null) {
                names.add(item.getDname());
              }
            });
    return names;
  }

  private Set<String> heroDisplayNames() {
    Set<String> names = new LinkedHashSet<>();
    constantsManagers
        .getHeroConstantMap()
        .values()
        .forEach(
            hero -> {
              if (hero.getLocalized_name() != null) {
                names.add(hero.getLocalized_name());
              }
            });
    return names;
  }

  private Set<String> abilityNames() {
    Set<String> names = new LinkedHashSet<>();
    constantsManagers
        .getAbilityConstantMap()
        .values()
        .forEach(
            ability -> {
              if (ability.getName() != null) {
                names.add(ability.getName());
              }
            });
    return names;
  }

  private Optional<String> closest(String unknown, Set<String> candidates) {
    if (unknown == null || unknown.isBlank()) {
      return Optional.empty();
    }
    String needle = unknown.trim().toLowerCase();

    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      int distance = editDistance(needle, candidate.toLowerCase());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return bestDistance <= MAX_SUGGESTION_DISTANCE ? Optional.ofNullable(best) : Optional.empty();
  }

  /** Standard Levenshtein distance, two-row variant. */
  private static int editDistance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];

    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.constant.ConstantNameResolverTest"`

Expected: **PASS**, 13 tests.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/constant/NameResolution.java \
        server/src/main/java/com/ako/dbuff/service/constant/ConstantNameResolver.java \
        server/src/test/java/com/ako/dbuff/service/constant/ConstantNameResolverTest.java
git commit -m "feat: add ConstantNameResolver reporting unresolved constant names"
```

---

## Task 3: Hero filter and average use count on `ItemRankingRepository`

Two changes to one query, done together because they touch the same
`multiselect` and the same predicate list.

The hero filter must be applied in **two** places: the main aggregation *and*
the private `getTotalMatchCount` helper. Pick rate is `pickCount / totalMatches`,
so filtering only the numerator reports pick rates several times too low with no
error. This is the single most important assertion in this task.

**Files:**
- Modify: `server/src/main/java/com/ako/dbuff/resources/model/ItemRankingResponse.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/ItemRankingRepositoryTest.java` (extend)

- [ ] **Step 1: Write the failing tests**

Append these tests inside the existing `ItemRankingRepositoryTest` class. Reuse
its existing `@BeforeEach` fixtures and constants (`PLAYER_ID`, `BLINK_ITEM_ID`,
`BKB_ITEM_ID`, …). Read the existing fixture setup first so the hero IDs and
match IDs you add do not collide.

```java
  private static final Long INVOKER_HERO_ID = 74L;
  private static final Long ANTIMAGE_HERO_ID = 1L;

  @Nested
  @DisplayName("hero filter")
  class HeroFilter {

    @Test
    void heroFilter_restrictsRankingsToThatHero() {
      List<ItemRankingResponse> all =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);
      List<ItemRankingResponse> invokerOnly =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      assertThat(invokerOnly).isNotEmpty();
      assertThat(invokerOnly.size()).isLessThanOrEqualTo(all.size());
    }

    @Test
    void heroFilter_pickRateIsRelativeToThatHerosGamesOnly() {
      // Regression guard: getTotalMatchCount must receive the hero filter too.
      // Without it, pick rate is divided by the player's games across ALL heroes
      // and comes out far too low.
      List<ItemRankingResponse> invokerOnly =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      ItemRankingResponse alwaysBoughtOnInvoker =
          invokerOnly.stream()
              .filter(r -> r.getItemId().equals(BLINK_ITEM_ID))
              .findFirst()
              .orElseThrow();

      // Blink appears in every Invoker game in the fixture, so pick rate is 100%.
      assertThat(alwaysBoughtOnInvoker.getPickRate())
          .isEqualByComparingTo(BigDecimal.valueOf(100.00).setScale(2));
    }

    @Test
    void heroFilterMatchingNoGames_returnsEmpty() {
      List<ItemRankingResponse> none =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(999L), 10);

      assertThat(none).isEmpty();
    }

    @Test
    void nullHeroFilter_behavesAsBefore() {
      List<ItemRankingResponse> unfiltered =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      assertThat(unfiltered).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("average use count")
  class AverageUseCount {

    @Test
    void avgUseCount_isAveragedAcrossGames() {
      List<ItemRankingResponse> rankings =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BLINK_ITEM_ID), null, null, 10);

      assertThat(rankings).hasSize(1);
      assertThat(rankings.get(0).getAvgUseCount()).isNotNull();
    }

    @Test
    void avgUseCount_isNullWhenNoGameRecordedUses() {
      // BOOTS_ITEM_ID rows are persisted with useCount = null in the fixture.
      List<ItemRankingResponse> rankings =
          itemRankingRepository.findItemRankingsByPlayer(
              PLAYER_ID, null, null, Set.of(BOOTS_ITEM_ID), null, null, 10);

      assertThat(rankings).hasSize(1);
      assertThat(rankings.get(0).getAvgUseCount()).isNull();
    }
  }
```

You must also extend the existing `@BeforeEach` so the fixture supports these
assertions. Add to it:

- a `heroId` on every persisted `PlayerMatchStatisticDomain` (Invoker for some
  matches, Anti-Mage for others),
- `useCount` values on the Blink `ItemDomain` rows,
- `useCount` left null on the Boots rows.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.ItemRankingRepositoryTest"`

Expected: **compilation failure** — no 7-argument `findItemRankingsByPlayer`, and
`getAvgUseCount()` does not exist.

- [ ] **Step 3: Add `avgUseCount` to the response model**

In `server/src/main/java/com/ako/dbuff/resources/model/ItemRankingResponse.java`,
add after the existing `avgPurchaseTime` field:

```java
  /**
   * Average number of times the item was used per game, or null when no game in range recorded a
   * use count (unparsed matches leave it null). Null means "no data", not "never used".
   */
  private BigDecimal avgUseCount;
```

- [ ] **Step 4: Update `ItemRankingRepository`**

In `server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java`:

**4a.** Change the method signature and Javadoc:

```java
  /**
   * Finds item rankings for a specific player with optional filters.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive)
   * @param endDate Optional end date filter (inclusive)
   * @param itemIds Optional set of item IDs to include (if null, returns top items by pick rate)
   * @param excludedItems Optional set of item IDs to exclude
   * @param heroIds Optional set of hero IDs to restrict the query to
   * @param limit Maximum number of items to return (default 10)
   * @return List of ItemRankingResponse ordered by pick count descending
   */
  public List<ItemRankingResponse> findItemRankingsByPlayer(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<Long> itemIds,
      Set<Long> excludedItems,
      Set<Long> heroIds,
      Integer limit) {
```

**4b.** Pass the hero filter to the total-match count — change:

```java
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate);
```

to:

```java
    Long totalMatches = getTotalMatchCount(playerId, startDate, endDate, heroIds);
```

**4c.** Add the hero predicate to the main query, immediately after the
`is_neutral` predicate:

```java
    // Hero filter
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
```

**4d.** Add the average to the `multiselect`, after `avgPurchaseTime`:

```java
        cb.avg(itemRoot.get(ItemDomain_.useCount)).alias("avgUseCount"));
```

(Remember to remove the closing paren from the previous line.)

**4e.** Update `getTotalMatchCount` to accept and apply the filter:

```java
  /** Gets the total number of matches for a player within the date range and hero filter. */
  private Long getTotalMatchCount(
      Long playerId, LocalDate startDate, LocalDate endDate, Set<Long> heroIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<PlayerMatchStatisticDomain> statsRoot = countQuery.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = countQuery.from(MatchDomain.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId));
    predicates.add(
        cb.equal(
            statsRoot.get(PlayerMatchStatisticDomain_.matchId), matchRoot.get(MatchDomain_.id)));

    if (startDate != null) {
      predicates.add(
          cb.greaterThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), startDate));
    }
    if (endDate != null) {
      predicates.add(cb.lessThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), endDate));
    }
    // Must mirror the main query's hero filter, or pick rate is divided by the
    // player's games across all heroes and comes out far too low.
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }

    countQuery.select(cb.countDistinct(statsRoot.get(PlayerMatchStatisticDomain_.matchId)));
    countQuery.where(predicates.toArray(new Predicate[0]));

    return entityManager.createQuery(countQuery).getSingleResult();
  }
```

**4f.** Map the new column in `mapToItemRankingResponse` — add before the
`.build()`:

```java
    Double avgUseCount = tuple.get("avgUseCount", Double.class);
```

and in the builder chain:

```java
        .avgUseCount(
            avgUseCount != null
                ? BigDecimal.valueOf(avgUseCount).setScale(2, RoundingMode.HALF_UP)
                : null)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.ItemRankingRepositoryTest"`

Expected: **PASS**, including all pre-existing tests in the class. Any
pre-existing test calling the 6-argument form needs `null` inserted before
`limit` — do that, do not delete the test.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java \
        server/src/main/java/com/ako/dbuff/resources/model/ItemRankingResponse.java \
        server/src/test/java/com/ako/dbuff/dao/repo/ItemRankingRepositoryTest.java
git commit -m "feat: add hero filter and average use count to item rankings"
```

---

## Task 4: Hero filter and average use count on `AbilityRankingRepository`

Structurally identical to Task 3. Abilities have no purchase-time analogue, so
only `avgUseCount` is added.

**Files:**
- Modify: `server/src/main/java/com/ako/dbuff/resources/model/AbilityRankingResponse.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/AbilityRankingRepository.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/AbilityRankingRepositoryTest.java` (extend)

- [ ] **Step 1: Write the failing tests**

Append inside the existing `AbilityRankingRepositoryTest` class, reusing its
fixtures and constants:

```java
  private static final Long INVOKER_HERO_ID = 74L;

  @Nested
  @DisplayName("hero filter")
  class HeroFilter {

    @Test
    void heroFilter_restrictsRankingsToThatHero() {
      List<AbilityRankingResponse> invokerOnly =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      assertThat(invokerOnly).isNotEmpty();
    }

    @Test
    void heroFilter_pickRateIsRelativeToThatHerosGamesOnly() {
      // Regression guard for getTotalMatchCount, same trap as the item repository.
      List<AbilityRankingResponse> invokerOnly =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(INVOKER_HERO_ID), 10);

      assertThat(invokerOnly)
          .allSatisfy(
              ranking ->
                  assertThat(ranking.getPickRate())
                      .isLessThanOrEqualTo(BigDecimal.valueOf(100.00).setScale(2)));
    }

    @Test
    void heroFilterMatchingNoGames_returnsEmpty() {
      List<AbilityRankingResponse> none =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, Set.of(999L), 10);

      assertThat(none).isEmpty();
    }
  }

  @Nested
  @DisplayName("average use count")
  class AverageUseCount {

    @Test
    void avgUseCount_isAveragedAcrossGames() {
      List<AbilityRankingResponse> rankings =
          abilityRankingRepository.findAbilityRankingsByPlayer(
              PLAYER_ID, null, null, null, null, null, 10);

      assertThat(rankings).isNotEmpty();
      assertThat(rankings.get(0).getAvgUseCount()).isNotNull();
    }
  }
```

Extend the existing `@BeforeEach` to set `heroId` on the persisted
`PlayerMatchStatisticDomain` rows and `useCount` on the `AbilityDomain` rows.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.AbilityRankingRepositoryTest"`

Expected: **compilation failure** — no 7-argument method, no `getAvgUseCount()`.

- [ ] **Step 3: Add `avgUseCount` to the response model**

In `server/src/main/java/com/ako/dbuff/resources/model/AbilityRankingResponse.java`,
add after `winRate`:

```java
  /**
   * Average number of times the ability was used per game, or null when no game in range recorded a
   * use count. Null means "no data", not "never used".
   */
  private BigDecimal avgUseCount;
```

- [ ] **Step 4: Update `AbilityRankingRepository`**

Apply exactly the same six edits as Task 3, substituting `abilityRoot` for
`itemRoot` and `AbilityDomain_` for `ItemDomain_`:

**4a.** Signature gains `Set<Long> heroIds` after `excludedAbilities`, with the
matching `@param heroIds Optional set of hero IDs to restrict the query to`.

**4b.** `getTotalMatchCount(playerId, startDate, endDate)` becomes
`getTotalMatchCount(playerId, startDate, endDate, heroIds)`.

**4c.** After the excluded-abilities predicate, add:

```java
    // Hero filter
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
```

**4d.** Extend the `multiselect`, replacing the `winCount` line's closing paren:

```java
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"),
        cb.avg(abilityRoot.get(AbilityDomain_.useCount)).alias("avgUseCount"));
```

**4e.** `getTotalMatchCount` gains the `Set<Long> heroIds` parameter and this
predicate, with the same comment as Task 3 explaining why it must mirror the main
query:

```java
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
```

**4f.** In `mapToAbilityRankingResponse`:

```java
    Double avgUseCount = tuple.get("avgUseCount", Double.class);
```

and in the builder chain:

```java
        .avgUseCount(
            avgUseCount != null
                ? BigDecimal.valueOf(avgUseCount).setScale(2, RoundingMode.HALF_UP)
                : null)
```

Confirm `java.math.RoundingMode` and `java.math.BigDecimal` are imported; add them
if not.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.AbilityRankingRepositoryTest"`

Expected: **PASS**. Insert `null` before `limit` in any pre-existing test calls.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/dao/repo/AbilityRankingRepository.java \
        server/src/main/java/com/ako/dbuff/resources/model/AbilityRankingResponse.java \
        server/src/test/java/com/ako/dbuff/dao/repo/AbilityRankingRepositoryTest.java
git commit -m "feat: add hero filter and average use count to ability rankings"
```

---

## Task 5: Hero filter on `PlayerStatisticRepository`

Powers `/stats overall hero:X`. When a hero filter is active the `popularHeroes`
list degenerates to a single entry, so the response also carries a flag telling
callers to omit that section.

**Files:**
- Modify: `server/src/main/java/com/ako/dbuff/resources/model/PlayerStatisticResponse.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/PlayerStatisticRepository.java`
- Modify: `server/src/main/java/com/ako/dbuff/service/ranking/PlayerStatisticService.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/PlayerStatisticRepositoryTest.java` (extend)

- [ ] **Step 1: Write the failing tests**

Append inside the existing `PlayerStatisticRepositoryTest` class:

```java
  private static final Long INVOKER_HERO_ID = 74L;

  @Nested
  @DisplayName("hero filter")
  class HeroFilter {

    @Test
    void heroFilter_countsOnlyThatHerosMatches() {
      PlayerStatisticResponse all =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null);
      PlayerStatisticResponse invokerOnly =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(INVOKER_HERO_ID));

      assertThat(invokerOnly.getTotalMatches()).isLessThan(all.getTotalMatches());
      assertThat(invokerOnly.getTotalMatches()).isPositive();
    }

    @Test
    void heroFilter_setsHeroFilteredFlag() {
      PlayerStatisticResponse invokerOnly =
          playerStatisticRepository.findPlayerStatistics(
              PLAYER_ID, null, null, 3, Set.of(INVOKER_HERO_ID));

      assertThat(invokerOnly.getHeroFiltered()).isTrue();
    }

    @Test
    void noHeroFilter_leavesFlagFalse() {
      PlayerStatisticResponse all =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, null);

      assertThat(all.getHeroFiltered()).isFalse();
    }

    @Test
    void heroFilterMatchingNoGames_returnsZeroMatches() {
      PlayerStatisticResponse none =
          playerStatisticRepository.findPlayerStatistics(PLAYER_ID, null, null, 3, Set.of(999L));

      assertThat(none.getTotalMatches()).isZero();
    }
  }
```

Extend the existing `@BeforeEach` so the player has matches on at least two
different `heroId` values.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.PlayerStatisticRepositoryTest"`

Expected: **compilation failure** — no 5-argument `findPlayerStatistics`, no
`getHeroFiltered()`.

- [ ] **Step 3: Add the flag to the response model**

In `server/src/main/java/com/ako/dbuff/resources/model/PlayerStatisticResponse.java`,
add:

```java
  /**
   * True when these statistics were restricted to one hero. Callers should omit the popularHeroes
   * section in that case — it degenerates to a single entry and reads as noise.
   */
  @Builder.Default private Boolean heroFiltered = false;
```

- [ ] **Step 4: Update `PlayerStatisticRepository`**

**4a.** Signature gains `Set<Long> heroIds` as the final parameter:

```java
  /**
   * Finds aggregated player statistics for a specific player within a date range.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive)
   * @param endDate Optional end date filter (inclusive)
   * @param heroLimit Number of popular heroes to return (default 3)
   * @param heroIds Optional set of hero IDs to restrict the query to
   * @return PlayerStatisticResponse with aggregated statistics
   */
  public PlayerStatisticResponse findPlayerStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Integer heroLimit,
      Set<Long> heroIds) {
```

**4b.** Add `import java.util.Set;` if absent.

**4c.** In **every** private helper that builds predicates on
`PlayerMatchStatisticDomain` — the main aggregation, `getTotalMatchCount`, and the
popular-heroes query — add the `Set<Long> heroIds` parameter and this predicate:

```java
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }
```

Read the file and apply this to each one. Missing any helper produces internally
inconsistent statistics — for example a total-match count that disagrees with the
win/loss sum.

**4d.** Set the flag on every returned response, including the early-return
zero-matches branch:

```java
        .heroFiltered(heroIds != null && !heroIds.isEmpty())
```

- [ ] **Step 5: Update `PlayerStatisticService`**

In `server/src/main/java/com/ako/dbuff/service/ranking/PlayerStatisticService.java`,
change `getPlayerStatistics` to accept and forward hero names, resolving them
through `ConstantNameResolver`:

```java
  @Transactional(readOnly = true)
  public PlayerStatisticResponse getPlayerStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Integer heroLimit,
      Set<String> heroNames) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveHeroLimit = heroLimit != null && heroLimit > 0 ? heroLimit : DEFAULT_HERO_LIMIT;

    NameResolution heroes = nameResolver.resolveHeroes(heroNames);
    if (heroes.hasUnresolved()) {
      throw new UnknownConstantNameException("heroes", heroes.unresolvedNames());
    }

    PlayerStatisticResponse statistics =
        playerStatisticRepository.findPlayerStatistics(
            playerId, startDate, effectiveEndDate, effectiveHeroLimit, heroes.idsOrNullIfEmpty());

    log.info(
        "Found statistics for player {} with {} total matches",
        playerId,
        statistics.getTotalMatches());

    return statistics;
  }
```

Add the `ConstantNameResolver nameResolver` final field, and these imports:

```java
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.util.Set;
```

Then update the internal callers of `getPlayerStatistics` inside this same class —
`persistPlayerStatistics` passes `null` for `heroNames`, since persisted summaries
are never hero-scoped.

- [ ] **Step 6: Update remaining callers**

Run: `grep -rn "findPlayerStatistics\|getPlayerStatistics" server/src/main/java/`

Pass `null` for the new trailing parameter at every hit outside the two files
already edited.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.PlayerStatisticRepositoryTest"`

Expected: **PASS**, pre-existing tests included (insert `null` as their fifth
argument).

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/dao/repo/PlayerStatisticRepository.java \
        server/src/main/java/com/ako/dbuff/resources/model/PlayerStatisticResponse.java \
        server/src/main/java/com/ako/dbuff/service/ranking/PlayerStatisticService.java \
        server/src/test/java/com/ako/dbuff/dao/repo/PlayerStatisticRepositoryTest.java
git commit -m "feat: add hero filter to player statistics"
```

---

## Task 6: Wire the resolver into the ranking services

Replaces the silent-drop conversion in both ranking services. Callers now get a
clear exception naming what failed instead of statistics for a different question.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/service/ranking/UnknownConstantNameException.java`
- Modify: `server/src/main/java/com/ako/dbuff/service/ranking/ItemRankingService.java` — replace `convertDnamesToIds`
- Modify: `server/src/main/java/com/ako/dbuff/service/ranking/AbilityRankingService.java` — replace `convertNamesToIds`
- Test: `server/src/test/java/com/ako/dbuff/service/ranking/ItemRankingServiceTest.java`
- Test: `server/src/test/java/com/ako/dbuff/service/ranking/AbilityRankingServiceTest.java`

- [ ] **Step 1: Write the failing test for items**

Create `server/src/test/java/com/ako/dbuff/service/ranking/ItemRankingServiceTest.java`:

```java
package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.ItemRankingRepository;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemRankingServiceTest {

  private static final Long PLAYER_ID = 123L;

  private ItemRankingRepository repository;
  private ConstantNameResolver resolver;
  private ItemRankingService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ItemRankingRepository.class);
    resolver = Mockito.mock(ConstantNameResolver.class);
    service = new ItemRankingService(repository, resolver);

    Mockito.when(repository.findItemRankingsByPlayer(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(List.of());
  }

  @Test
  void unknownItemName_throwsInsteadOfSilentlyWideningTheQuery() {
    Mockito.when(resolver.resolveItems(Set.of("garbage")))
        .thenReturn(new NameResolution(Set.of(), Set.of("garbage")));
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, Set.of("garbage"), null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage");

    Mockito.verify(repository, Mockito.never())
        .findItemRankingsByPlayer(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void partiallyUnknownItemNames_throwsNamingOnlyTheBadOne() {
    Mockito.when(resolver.resolveItems(Set.of("blink", "garbage")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of("garbage")));
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, Set.of("blink", "garbage"), null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("garbage")
        .hasMessageNotContaining("blink");
  }

  @Test
  void knownItemNames_passResolvedIdsToRepository() {
    Mockito.when(resolver.resolveItems(Set.of("blink")))
        .thenReturn(new NameResolution(Set.of(1L), Set.of()));
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());

    service.getItemRankings(PLAYER_ID, null, null, Set.of("blink"), null, null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> itemIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findItemRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(),
            itemIds.capture(), Mockito.any(), Mockito.any(), Mockito.any());

    assertThat(itemIds.getValue()).containsExactly(1L);
  }

  @Test
  void noItemFilter_passesNullSoRepositoryReturnsTopN() {
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());

    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> itemIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findItemRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(),
            itemIds.capture(), Mockito.any(), Mockito.any(), Mockito.any());

    assertThat(itemIds.getValue()).isNull();
  }

  @Test
  void unknownHeroName_throws() {
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(Set.of("Not A Hero")))
        .thenReturn(new NameResolution(Set.of(), Set.of("Not A Hero")));

    assertThatThrownBy(
            () ->
                service.getItemRankings(
                    PLAYER_ID, null, null, null, null, Set.of("Not A Hero"), null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("Not A Hero");
  }

  @Test
  void heroFilter_isForwardedToRepository() {
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    service.getItemRankings(PLAYER_ID, null, null, null, null, Set.of("Invoker"), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> heroIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findItemRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), heroIds.capture(), Mockito.any());

    assertThat(heroIds.getValue()).containsExactly(74L);
  }

  @Test
  void endDateDefaultsToToday() {
    Mockito.when(resolver.resolveItems(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());

    service.getItemRankings(PLAYER_ID, null, null, null, null, null, null);

    ArgumentCaptor<LocalDate> endDate = ArgumentCaptor.forClass(LocalDate.class);
    Mockito.verify(repository)
        .findItemRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), endDate.capture(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any());

    assertThat(endDate.getValue()).isEqualTo(LocalDate.now());
  }
}
```

> **Depends on Tasks 3 and 4**, which add the `heroIds` parameter to
> `findItemRankingsByPlayer` and `findAbilityRankingsByPlayer` (inserted after
> `excludedItems`/`excludedAbilities`, before `limit`). Those tasks come first in
> this plan, so the 7-argument calls below will compile.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.ranking.ItemRankingServiceTest"`

Expected: **compilation failure** — `cannot find symbol: class UnknownConstantNameException`, and a constructor-arity error on `new ItemRankingService(repository, resolver)`.

- [ ] **Step 3: Write the exception**

Create `server/src/main/java/com/ako/dbuff/service/ranking/UnknownConstantNameException.java`:

```java
package com.ako.dbuff.service.ranking;

import java.util.Set;
import lombok.Getter;

/**
 * Thrown when a caller supplies item, hero, or ability names that match no known constant.
 *
 * <p>Extends {@link IllegalArgumentException} so existing REST error handling continues to treat it
 * as a client error. Carries the offending names so callers can render a useful message — the
 * Discord handlers use them to offer "did you mean" suggestions.
 */
@Getter
public class UnknownConstantNameException extends IllegalArgumentException {

  private final String constantType;
  private final Set<String> unknownNames;

  public UnknownConstantNameException(String constantType, Set<String> unknownNames) {
    super("Unknown " + constantType + ": " + String.join(", ", unknownNames));
    this.constantType = constantType;
    this.unknownNames = unknownNames;
  }
}
```

- [ ] **Step 4: Rewrite `ItemRankingService`**

Replace the whole file at
`server/src/main/java/com/ako/dbuff/service/ranking/ItemRankingService.java`:

```java
package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.ItemRankingRepository;
import com.ako.dbuff.resources.model.ItemRankingResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating item rankings per player. Provides statistics about item usage including
 * pick rate, win rate, average purchase time, and average uses per game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final ItemRankingRepository itemRankingRepository;
  private final ConstantNameResolver nameResolver;

  /**
   * Gets item rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param itemNames Optional item names to include. If null or empty, returns top items by pick
   *     count.
   * @param excludedItemNames Optional item names to exclude from results.
   * @param heroNames Optional hero names to restrict the query to.
   * @param limit Maximum number of items to return. Defaults to 10 if null.
   * @return List of ItemRankingResponse ordered by pick count descending
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public List<ItemRankingResponse> getItemRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> itemNames,
      Set<String> excludedItemNames,
      Set<String> heroNames,
      Integer limit) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    Set<Long> itemIds = resolveItemsOrThrow(itemNames);
    Set<Long> excludedItemIds = resolveItemsOrThrow(excludedItemNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);

    log.info(
        "Fetching item rankings for player {}: startDate={}, endDate={}, items={}, excluded={}, heroes={}, limit={}",
        playerId,
        startDate,
        effectiveEndDate,
        itemIds,
        excludedItemIds,
        heroIds,
        effectiveLimit);

    List<ItemRankingResponse> rankings =
        itemRankingRepository.findItemRankingsByPlayer(
            playerId,
            startDate,
            effectiveEndDate,
            itemIds,
            excludedItemIds,
            heroIds,
            effectiveLimit);

    log.info("Found {} item rankings for player {}", rankings.size(), playerId);
    return rankings;
  }

  private Set<Long> resolveItemsOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveItems(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("items", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }

  private Set<Long> resolveHeroesOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveHeroes(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("heroes", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }
}
```

- [ ] **Step 5: Update the callers of `getItemRankings`**

The signature gained a `heroNames` parameter. Find every caller and pass `null`:

Run: `grep -rn "getItemRankings" server/src/main/java/`

For each hit outside `ItemRankingService` itself, insert `null,` before the `limit`
argument. Expected: the item ranking REST controller in
`com.ako.dbuff.resources`. Do not change its HTTP contract in this task — a
`heroes` query parameter is out of scope here.

- [ ] **Step 6: Write the failing test for abilities**

Create `server/src/test/java/com/ako/dbuff/service/ranking/AbilityRankingServiceTest.java`:

```java
package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbilityRankingServiceTest {

  private static final Long PLAYER_ID = 123L;

  private AbilityRankingRepository repository;
  private ConstantNameResolver resolver;
  private AbilityRankingService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AbilityRankingRepository.class);
    resolver = Mockito.mock(ConstantNameResolver.class);
    service = new AbilityRankingService(repository, resolver);

    Mockito.when(repository.findAbilityRankingsByPlayer(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(List.of());
    Mockito.when(resolver.resolveAbilities(null)).thenReturn(NameResolution.empty());
    Mockito.when(resolver.resolveHeroes(null)).thenReturn(NameResolution.empty());
  }

  @Test
  void unknownAbilityName_throwsInsteadOfSilentlyWideningTheQuery() {
    Mockito.when(resolver.resolveAbilities(Set.of("made_up_spell")))
        .thenReturn(new NameResolution(Set.of(), Set.of("made_up_spell")));

    assertThatThrownBy(
            () ->
                service.getAbilityRankings(
                    PLAYER_ID, null, null, Set.of("made_up_spell"), null, null, null))
        .isInstanceOf(UnknownConstantNameException.class)
        .hasMessageContaining("made_up_spell");

    Mockito.verify(repository, Mockito.never())
        .findAbilityRankingsByPlayer(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void knownAbilityNames_passResolvedIdsToRepository() {
    Mockito.when(resolver.resolveAbilities(Set.of("invoker_quas")))
        .thenReturn(new NameResolution(Set.of(5001L), Set.of()));

    service.getAbilityRankings(PLAYER_ID, null, null, Set.of("invoker_quas"), null, null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> abilityIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findAbilityRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(),
            abilityIds.capture(), Mockito.any(), Mockito.any(), Mockito.any());

    assertThat(abilityIds.getValue()).containsExactly(5001L);
  }

  @Test
  void noAbilityFilter_passesNullSoRepositoryReturnsTopN() {
    service.getAbilityRankings(PLAYER_ID, null, null, null, null, null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> abilityIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findAbilityRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(),
            abilityIds.capture(), Mockito.any(), Mockito.any(), Mockito.any());

    assertThat(abilityIds.getValue()).isNull();
  }

  @Test
  void heroFilter_isForwardedToRepository() {
    Mockito.when(resolver.resolveHeroes(Set.of("Invoker")))
        .thenReturn(new NameResolution(Set.of(74L), Set.of()));

    service.getAbilityRankings(PLAYER_ID, null, null, null, null, Set.of("Invoker"), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<Long>> heroIds = ArgumentCaptor.forClass(Set.class);
    Mockito.verify(repository)
        .findAbilityRankingsByPlayer(
            Mockito.eq(PLAYER_ID), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), heroIds.capture(), Mockito.any());

    assertThat(heroIds.getValue()).containsExactly(74L);
  }
}
```

- [ ] **Step 7: Rewrite `AbilityRankingService` the same way**

Replace the whole file at
`server/src/main/java/com/ako/dbuff/service/ranking/AbilityRankingService.java`:

```java
package com.ako.dbuff.service.ranking;

import com.ako.dbuff.dao.repo.AbilityRankingRepository;
import com.ako.dbuff.resources.model.AbilityRankingResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.NameResolution;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating ability rankings per player. Provides statistics about ability usage
 * including pick rate, win rate, and average uses per game.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityRankingService {

  private static final int DEFAULT_LIMIT = 10;

  private final AbilityRankingRepository abilityRankingRepository;
  private final ConstantNameResolver nameResolver;

  /**
   * Gets ability rankings for a specific player.
   *
   * @param playerId The player's account ID
   * @param startDate Optional start date filter (inclusive). If null, includes all history.
   * @param endDate Optional end date filter (inclusive). If null, uses current date.
   * @param abilityNames Optional ability names to include. If null or empty, returns top abilities
   *     by pick count.
   * @param excludedAbilityNames Optional ability names to exclude from results.
   * @param heroNames Optional hero names to restrict the query to.
   * @param limit Maximum number of abilities to return. Defaults to 10 if null.
   * @return List of AbilityRankingResponse ordered by pick count descending
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public List<AbilityRankingResponse> getAbilityRankings(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> excludedAbilityNames,
      Set<String> heroNames,
      Integer limit) {

    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
    int effectiveLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> excludedAbilityIds = resolveAbilitiesOrThrow(excludedAbilityNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);

    log.info(
        "Fetching ability rankings for player {}: startDate={}, endDate={}, abilities={}, excluded={}, heroes={}, limit={}",
        playerId,
        startDate,
        effectiveEndDate,
        abilityIds,
        excludedAbilityIds,
        heroIds,
        effectiveLimit);

    List<AbilityRankingResponse> rankings =
        abilityRankingRepository.findAbilityRankingsByPlayer(
            playerId,
            startDate,
            effectiveEndDate,
            abilityIds,
            excludedAbilityIds,
            heroIds,
            effectiveLimit);

    log.info("Found {} ability rankings for player {}", rankings.size(), playerId);
    return rankings;
  }

  private Set<Long> resolveAbilitiesOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveAbilities(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("abilities", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }

  private Set<Long> resolveHeroesOrThrow(Set<String> names) {
    NameResolution resolution = nameResolver.resolveHeroes(names);
    if (resolution.hasUnresolved()) {
      throw new UnknownConstantNameException("heroes", resolution.unresolvedNames());
    }
    return resolution.idsOrNullIfEmpty();
  }
}
```

- [ ] **Step 8: Update the callers of `getAbilityRankings`**

Run: `grep -rn "getAbilityRankings" server/src/main/java/`

Insert `null,` before the `limit` argument at each hit outside the service itself.

- [ ] **Step 9: Run both tests**

Run: `./gradlew :server:test --tests "com.ako.dbuff.service.ranking.ItemRankingServiceTest" --tests "com.ako.dbuff.service.ranking.AbilityRankingServiceTest"`

Expected: **PASS**, 11 tests total. If the repository signatures do not yet have
`heroIds`, complete Task 5 and Task 6 first, then re-run.

- [ ] **Step 10: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/service/ranking/ \
        server/src/test/java/com/ako/dbuff/service/ranking/
git commit -m "fix: report unknown constant names instead of silently widening ranking queries"
```

---

## Task 7: Item combo statistics

Answers "how does this player do when they get Blink **and** BKB in the same
game?" — a fundamentally different question from the top-N ranking, which groups
*by item* and cannot express conjunction.

Implemented as two queries rather than one, which is both clearer and safer on H2:

1. From `item_domain` alone, find the match IDs where the player had **all**
   requested items, using `GROUP BY match_id HAVING COUNT(DISTINCT item_id) = n`.
2. Aggregate `player_match_statistic_domain` joined to `match_domain` over those
   match IDs, applying the date and hero filters there.

`COUNT(DISTINCT item_id)` is load-bearing. With plain `COUNT(*)`, two rows for the
same item in one game would satisfy a two-item query, reporting single-item games
as combo games.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/resources/model/ItemComboStatisticResponse.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java`
- Modify: `server/src/main/java/com/ako/dbuff/service/ranking/ItemRankingService.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/ItemComboStatisticRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/dao/repo/ItemComboStatisticRepositoryTest.java`:

```java
package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the conjunctive combo query: statistics over games containing ALL requested items.
 *
 * <p>Fixture layout (player 123, slot 0 in every match):
 *
 * <ul>
 *   <li>match 1 — Invoker, WIN, has Blink + BKB
 *   <li>match 2 — Invoker, LOSS, has Blink + BKB
 *   <li>match 3 — Invoker, WIN, has Blink only
 *   <li>match 4 — Anti-Mage, WIN, has Blink + BKB
 *   <li>match 5 — Invoker, WIN, has Blink twice (duplicate rows, no BKB)
 * </ul>
 */
@DataJpaTest
@Import(ItemRankingRepository.class)
@ActiveProfiles("test")
class ItemComboStatisticRepositoryTest {

  private static final Long PLAYER_ID = 123L;
  private static final String PLAYER_NAME = "TestPlayer";
  private static final Long BLINK = 100L;
  private static final Long BKB = 200L;
  private static final Long INVOKER = 74L;
  private static final Long ANTIMAGE = 1L;

  @Autowired private EntityManager entityManager;
  @Autowired private ItemRankingRepository itemRankingRepository;

  @BeforeEach
  void setUp() {
    entityManager.persist(PlayerDomain.builder().id(PLAYER_ID).name(PLAYER_NAME).build());

    match(1L, INVOKER, 1L);
    match(2L, INVOKER, 0L);
    match(3L, INVOKER, 1L);
    match(4L, ANTIMAGE, 1L);
    match(5L, INVOKER, 1L);

    item(1L, BLINK, 400L, 3L);
    item(1L, BKB, 900L, 1L);
    item(2L, BLINK, 500L, 2L);
    item(2L, BKB, 1000L, 2L);
    item(3L, BLINK, 450L, 4L);
    item(4L, BLINK, 420L, 1L);
    item(4L, BKB, 950L, 1L);
    // Duplicate Blink rows in one game — must NOT satisfy a two-item query.
    item(5L, BLINK, 400L, 1L);
    item(5L, BLINK, 800L, 2L);

    entityManager.flush();
  }

  private void match(Long matchId, Long heroId, Long win) {
    entityManager.persist(
        MatchDomain.builder().id(matchId).startLocalDate(LocalDate.of(2026, 8, 10)).build());
    entityManager.persist(
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .playerId(PLAYER_ID)
            .heroId(heroId)
            .win(win)
            .kda(BigDecimal.valueOf(3.0))
            .build());
  }

  private void item(Long matchId, Long itemId, Long purchaseTime, Long useCount) {
    entityManager.persist(
        ItemDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .itemId(itemId)
            .playerId(PLAYER_ID)
            .itemPurchaseTime(purchaseTime)
            .useCount(useCount)
            .isNeutral(false)
            .build());
  }

  @Test
  void requiresAllItemsInTheSameGame() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null);

    // Matches 1, 2 and 4 have both items. Match 3 has only Blink; match 5 has
    // Blink twice but no BKB.
    assertThat(result.getGamesFound()).isEqualTo(3L);
  }

  @Test
  void duplicateRowsForOneItemDoNotSatisfyATwoItemQuery() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null);

    assertThat(result.getMatchIds()).doesNotContain(5L);
  }

  @Test
  void winRateIsComputedOverComboGamesOnly() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, null, null);

    // Matches 1 and 4 won, match 2 lost -> 2/3 = 66.67%
    assertThat(result.getWinRate())
        .isEqualByComparingTo(BigDecimal.valueOf(66.67).setScale(2));
  }

  @Test
  void heroFilterNarrowsComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), Set.of(INVOKER), null, null);

    // Only matches 1 and 2 are Invoker games with both items.
    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
  }

  @Test
  void perItemAveragesCoverOnlyComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), Set.of(INVOKER), null, null);

    ItemComboStatisticResponse.Member blink =
        result.getMembers().stream()
            .filter(m -> m.getItemId().equals(BLINK))
            .findFirst()
            .orElseThrow();

    // Invoker combo games are 1 and 2: purchase times 400 and 500 -> 450
    assertThat(blink.getAvgPurchaseTime())
        .isEqualByComparingTo(BigDecimal.valueOf(450.00).setScale(2));
    // use counts 3 and 2 -> 2.50
    assertThat(blink.getAvgUseCount()).isEqualByComparingTo(BigDecimal.valueOf(2.50).setScale(2));
  }

  @Test
  void singleItemDegeneratesToGamesWithThatItem() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(PLAYER_ID, Set.of(BKB), null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
  }

  @Test
  void noComboGames_returnsZeroNotNull() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB, 999L), null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getMembers()).isEmpty();
  }

  @Test
  void dateRangeExcludesOlderComboGames() {
    ItemComboStatisticResponse result =
        itemRankingRepository.findItemComboStatistics(
            PLAYER_ID, Set.of(BLINK, BKB), null, LocalDate.of(2026, 8, 15), null);

    assertThat(result.getGamesFound()).isZero();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.ItemComboStatisticRepositoryTest"`

Expected: **compilation failure** — `cannot find symbol: class ItemComboStatisticResponse`.

- [ ] **Step 3: Create the response model**

Create `server/src/main/java/com/ako/dbuff/resources/model/ItemComboStatisticResponse.java`:

```java
package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics over the games in which a player held every one of a requested set of items. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemComboStatisticResponse {

  private Long playerId;
  private String playerName;

  /** Number of games containing every requested item. */
  private Long gamesFound;

  /** Match IDs of those games, for drill-down and for asserting exclusions in tests. */
  private Set<Long> matchIds;

  /** Win percentage across the combo games, 0–100. Zero when no games matched. */
  private BigDecimal winRate;

  /** Average KDA across the combo games, or null when no games matched. */
  private BigDecimal avgKda;

  /** Per-item detail, one entry per requested item that appeared. */
  private List<Member> members;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Member {
    private Long itemId;
    private String itemName;
    private String itemPrettyName;

    /** Average purchase time in seconds across the combo games. */
    private BigDecimal avgPurchaseTime;

    /** Average uses per game across the combo games, or null when no use data was recorded. */
    private BigDecimal avgUseCount;
  }
}
```

- [ ] **Step 4: Add the query to `ItemRankingRepository`**

Append this method to
`server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java`:

```java
  /**
   * Finds statistics over the games in which the player held EVERY one of {@code itemIds}.
   *
   * <p>Two-step by design: step one asks {@code item_domain} alone which matches contain the full
   * set, step two aggregates the player's statistics over those matches with the date and hero
   * filters applied. Doing it in one query would need a correlated having-clause across three cross
   * joins, which is harder to read and less portable.
   *
   * @param playerId the player's account ID
   * @param itemIds the items that must ALL be present; empty or null yields zero games
   * @param heroIds optional hero restriction
   * @param startDate optional inclusive lower bound on match date
   * @param endDate optional inclusive upper bound on match date
   * @return combo statistics, never null; {@code gamesFound} is 0 when nothing matched
   */
  public ItemComboStatisticResponse findItemComboStatistics(
      Long playerId,
      Set<Long> itemIds,
      Set<Long> heroIds,
      LocalDate startDate,
      LocalDate endDate) {

    String playerName = getPlayerName(playerId);
    ItemComboStatisticResponse empty =
        ItemComboStatisticResponse.builder()
            .playerId(playerId)
            .playerName(playerName)
            .gamesFound(0L)
            .matchIds(Set.of())
            .winRate(BigDecimal.ZERO)
            .members(List.of())
            .build();

    if (itemIds == null || itemIds.isEmpty()) {
      return empty;
    }

    Set<Long> candidateMatchIds = findMatchesContainingAllItems(playerId, itemIds);
    if (candidateMatchIds.isEmpty()) {
      return empty;
    }

    Set<Long> comboMatchIds =
        applyDateAndHeroFilters(playerId, candidateMatchIds, heroIds, startDate, endDate);
    if (comboMatchIds.isEmpty()) {
      return empty;
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // Win rate and average KDA over the combo games.
    CriteriaQuery<Tuple> statsQuery = cb.createTupleQuery();
    Root<PlayerMatchStatisticDomain> statsRoot = statsQuery.from(PlayerMatchStatisticDomain.class);
    statsQuery.multiselect(
        cb.sum(statsRoot.get(PlayerMatchStatisticDomain_.win)).alias("winCount"),
        cb.avg(statsRoot.get(PlayerMatchStatisticDomain_.kda)).alias("avgKda"));
    statsQuery.where(
        cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId),
        statsRoot.get(PlayerMatchStatisticDomain_.matchId).in(comboMatchIds));

    Tuple stats = entityManager.createQuery(statsQuery).getSingleResult();
    Long winCount = stats.get("winCount", Long.class);
    Double avgKda = stats.get("avgKda", Double.class);

    long gamesFound = comboMatchIds.size();
    BigDecimal winRate =
        BigDecimal.valueOf(winCount != null ? winCount : 0L)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(gamesFound), 2, RoundingMode.HALF_UP);

    // Per-item averages over the combo games only.
    CriteriaQuery<Tuple> memberQuery = cb.createTupleQuery();
    Root<ItemDomain> itemRoot = memberQuery.from(ItemDomain.class);
    memberQuery.multiselect(
        itemRoot.get(ItemDomain_.itemId).alias("itemId"),
        itemRoot.get(ItemDomain_.itemName).alias("itemName"),
        itemRoot.get(ItemDomain_.itemPrettyName).alias("itemPrettyName"),
        cb.avg(itemRoot.get(ItemDomain_.itemPurchaseTime)).alias("avgPurchaseTime"),
        cb.avg(itemRoot.get(ItemDomain_.useCount)).alias("avgUseCount"));
    memberQuery.where(
        cb.equal(itemRoot.get(ItemDomain_.playerId), playerId),
        cb.equal(itemRoot.get(ItemDomain_.isNeutral), false),
        itemRoot.get(ItemDomain_.itemId).in(itemIds),
        itemRoot.get(ItemDomain_.matchId).in(comboMatchIds));
    memberQuery.groupBy(
        itemRoot.get(ItemDomain_.itemId),
        itemRoot.get(ItemDomain_.itemName),
        itemRoot.get(ItemDomain_.itemPrettyName));

    List<ItemComboStatisticResponse.Member> members =
        entityManager.createQuery(memberQuery).getResultList().stream()
            .map(
                tuple -> {
                  Double avgPurchase = tuple.get("avgPurchaseTime", Double.class);
                  Double avgUse = tuple.get("avgUseCount", Double.class);
                  return ItemComboStatisticResponse.Member.builder()
                      .itemId(tuple.get("itemId", Long.class))
                      .itemName(tuple.get("itemName", String.class))
                      .itemPrettyName(tuple.get("itemPrettyName", String.class))
                      .avgPurchaseTime(
                          avgPurchase != null
                              ? BigDecimal.valueOf(avgPurchase).setScale(2, RoundingMode.HALF_UP)
                              : null)
                      .avgUseCount(
                          avgUse != null
                              ? BigDecimal.valueOf(avgUse).setScale(2, RoundingMode.HALF_UP)
                              : null)
                      .build();
                })
            .toList();

    return ItemComboStatisticResponse.builder()
        .playerId(playerId)
        .playerName(playerName)
        .gamesFound(gamesFound)
        .matchIds(comboMatchIds)
        .winRate(winRate)
        .avgKda(
            avgKda != null ? BigDecimal.valueOf(avgKda).setScale(2, RoundingMode.HALF_UP) : null)
        .members(members)
        .build();
  }

  /**
   * Match IDs where the player held every one of {@code itemIds}.
   *
   * <p>{@code countDistinct(itemId)} rather than {@code count(itemId)} is essential: a game with two
   * rows for the same item would otherwise satisfy a two-item request.
   */
  private Set<Long> findMatchesContainingAllItems(Long playerId, Set<Long> itemIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<ItemDomain> itemRoot = query.from(ItemDomain.class);

    query.select(itemRoot.get(ItemDomain_.matchId));
    query.where(
        cb.equal(itemRoot.get(ItemDomain_.playerId), playerId),
        cb.equal(itemRoot.get(ItemDomain_.isNeutral), false),
        itemRoot.get(ItemDomain_.itemId).in(itemIds));
    query.groupBy(itemRoot.get(ItemDomain_.matchId));
    query.having(
        cb.equal(
            cb.countDistinct(itemRoot.get(ItemDomain_.itemId)), Long.valueOf(itemIds.size())));

    return new LinkedHashSet<>(entityManager.createQuery(query).getResultList());
  }

  /** Narrows candidate match IDs by match date and hero. */
  private Set<Long> applyDateAndHeroFilters(
      Long playerId,
      Set<Long> candidateMatchIds,
      Set<Long> heroIds,
      LocalDate startDate,
      LocalDate endDate) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<PlayerMatchStatisticDomain> statsRoot = query.from(PlayerMatchStatisticDomain.class);
    Root<MatchDomain> matchRoot = query.from(MatchDomain.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(statsRoot.get(PlayerMatchStatisticDomain_.playerId), playerId));
    predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.matchId).in(candidateMatchIds));
    predicates.add(
        cb.equal(
            statsRoot.get(PlayerMatchStatisticDomain_.matchId), matchRoot.get(MatchDomain_.id)));

    if (startDate != null) {
      predicates.add(
          cb.greaterThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), startDate));
    }
    if (endDate != null) {
      predicates.add(cb.lessThanOrEqualTo(matchRoot.get(MatchDomain_.startLocalDate), endDate));
    }
    if (heroIds != null && !heroIds.isEmpty()) {
      predicates.add(statsRoot.get(PlayerMatchStatisticDomain_.heroId).in(heroIds));
    }

    query.select(statsRoot.get(PlayerMatchStatisticDomain_.matchId)).distinct(true);
    query.where(predicates.toArray(new Predicate[0]));

    return new LinkedHashSet<>(entityManager.createQuery(query).getResultList());
  }
```

Add these imports to the file if absent:

```java
import com.ako.dbuff.resources.model.ItemComboStatisticResponse;
import java.util.LinkedHashSet;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.ItemComboStatisticRepositoryTest"`

Expected: **PASS**, 8 tests.

- [ ] **Step 6: Expose it on the service**

Append to `server/src/main/java/com/ako/dbuff/service/ranking/ItemRankingService.java`:

```java
  /**
   * Gets statistics over the games in which the player held every one of the named items.
   *
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public ItemComboStatisticResponse getItemComboStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> itemNames,
      Set<String> heroNames) {

    Set<Long> itemIds = resolveItemsOrThrow(itemNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    log.info(
        "Fetching item combo statistics for player {}: items={}, heroes={}, {} to {}",
        playerId,
        itemIds,
        heroIds,
        startDate,
        effectiveEndDate);

    return itemRankingRepository.findItemComboStatistics(
        playerId, itemIds, heroIds, startDate, effectiveEndDate);
  }
```

Add `import com.ako.dbuff.resources.model.ItemComboStatisticResponse;`.

- [ ] **Step 7: Run the full suite**

Run: `./gradlew test`

Expected: **PASS**.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/resources/model/ItemComboStatisticResponse.java \
        server/src/main/java/com/ako/dbuff/dao/repo/ItemRankingRepository.java \
        server/src/main/java/com/ako/dbuff/service/ranking/ItemRankingService.java \
        server/src/test/java/com/ako/dbuff/dao/repo/ItemComboStatisticRepositoryTest.java
git commit -m "feat: add conjunctive item combo statistics query"
```

---

## Task 8: Ability combo statistics

The mirror of Task 7. `AbilityDomain`'s primary key is
`(matchId, playerSlot, abilityId)`, so the query is structurally identical.
Abilities have no purchase time, so members carry only `avgUseCount`.

**Files:**
- Create: `server/src/main/java/com/ako/dbuff/resources/model/AbilityComboStatisticResponse.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/AbilityRankingRepository.java`
- Modify: `server/src/main/java/com/ako/dbuff/service/ranking/AbilityRankingService.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/AbilityComboStatisticRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/dao/repo/AbilityComboStatisticRepositoryTest.java`.
Use the same fixture shape as `ItemComboStatisticRepositoryTest`, substituting
`AbilityDomain` for `ItemDomain`:

```java
package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture (player 123, slot 0):
 *
 * <ul>
 *   <li>match 1 — Invoker, WIN, has Quas + Wex
 *   <li>match 2 — Invoker, LOSS, has Quas + Wex
 *   <li>match 3 — Invoker, WIN, has Quas only
 *   <li>match 4 — Anti-Mage, WIN, has Quas + Wex
 * </ul>
 */
@DataJpaTest
@Import(AbilityRankingRepository.class)
@ActiveProfiles("test")
class AbilityComboStatisticRepositoryTest {

  private static final Long PLAYER_ID = 123L;
  private static final Long QUAS = 5001L;
  private static final Long WEX = 5002L;
  private static final Long INVOKER = 74L;
  private static final Long ANTIMAGE = 1L;

  @Autowired private EntityManager entityManager;
  @Autowired private AbilityRankingRepository abilityRankingRepository;

  @BeforeEach
  void setUp() {
    entityManager.persist(PlayerDomain.builder().id(PLAYER_ID).name("TestPlayer").build());

    match(1L, INVOKER, 1L);
    match(2L, INVOKER, 0L);
    match(3L, INVOKER, 1L);
    match(4L, ANTIMAGE, 1L);

    ability(1L, QUAS, "invoker_quas", 10L);
    ability(1L, WEX, "invoker_wex", 4L);
    ability(2L, QUAS, "invoker_quas", 20L);
    ability(2L, WEX, "invoker_wex", 6L);
    ability(3L, QUAS, "invoker_quas", 15L);
    ability(4L, QUAS, "invoker_quas", 5L);
    ability(4L, WEX, "invoker_wex", 5L);

    entityManager.flush();
  }

  private void match(Long matchId, Long heroId, Long win) {
    entityManager.persist(
        MatchDomain.builder().id(matchId).startLocalDate(LocalDate.of(2026, 8, 10)).build());
    entityManager.persist(
        PlayerMatchStatisticDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .playerId(PLAYER_ID)
            .heroId(heroId)
            .win(win)
            .kda(BigDecimal.valueOf(3.0))
            .build());
  }

  private void ability(Long matchId, Long abilityId, String name, Long useCount) {
    entityManager.persist(
        AbilityDomain.builder()
            .matchId(matchId)
            .playerSlot(0L)
            .abilityId(abilityId)
            .playerId(PLAYER_ID)
            .name(name)
            .prettyName(name)
            .useCount(useCount)
            .build());
  }

  @Test
  void requiresAllAbilitiesInTheSameGame() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null);

    assertThat(result.getGamesFound()).isEqualTo(3L);
    assertThat(result.getMatchIds()).doesNotContain(3L);
  }

  @Test
  void winRateIsComputedOverComboGamesOnly() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), null, null, null);

    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67).setScale(2));
  }

  @Test
  void heroFilterNarrowsComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(INVOKER), null, null);

    assertThat(result.getGamesFound()).isEqualTo(2L);
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
  }

  @Test
  void perAbilityAveragesCoverOnlyComboGames() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX), Set.of(INVOKER), null, null);

    AbilityComboStatisticResponse.Member quas =
        result.getMembers().stream()
            .filter(m -> m.getAbilityId().equals(QUAS))
            .findFirst()
            .orElseThrow();

    // Invoker combo games are 1 and 2: use counts 10 and 20 -> 15.00
    assertThat(quas.getAvgUseCount()).isEqualByComparingTo(BigDecimal.valueOf(15.00).setScale(2));
  }

  @Test
  void noComboGames_returnsZeroNotNull() {
    AbilityComboStatisticResponse result =
        abilityRankingRepository.findAbilityComboStatistics(
            PLAYER_ID, Set.of(QUAS, WEX, 9999L), null, null, null);

    assertThat(result.getGamesFound()).isZero();
    assertThat(result.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getMembers()).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.AbilityComboStatisticRepositoryTest"`

Expected: **compilation failure** — `cannot find symbol: class AbilityComboStatisticResponse`.

- [ ] **Step 3: Create the response model**

Create `server/src/main/java/com/ako/dbuff/resources/model/AbilityComboStatisticResponse.java`:

```java
package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistics over the games in which a player used every one of a requested set of abilities. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AbilityComboStatisticResponse {

  private Long playerId;
  private String playerName;

  /** Number of games containing every requested ability. */
  private Long gamesFound;

  /** Match IDs of those games. */
  private Set<Long> matchIds;

  /** Win percentage across the combo games, 0–100. Zero when no games matched. */
  private BigDecimal winRate;

  /** Average KDA across the combo games, or null when no games matched. */
  private BigDecimal avgKda;

  private List<Member> members;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Member {
    private Long abilityId;
    private String abilityName;
    private String abilityPrettyName;

    /** Average uses per game across the combo games, or null when no use data was recorded. */
    private BigDecimal avgUseCount;
  }
}
```

- [ ] **Step 4: Add the query to `AbilityRankingRepository`**

Append `findAbilityComboStatistics` plus its two private helpers
(`findMatchesContainingAllAbilities`, `applyDateAndHeroFilters`) to
`server/src/main/java/com/ako/dbuff/dao/repo/AbilityRankingRepository.java`.

Copy the three methods from Task 7 step 4 verbatim and apply exactly these
substitutions:

| Task 7 | Task 8 |
|---|---|
| `ItemComboStatisticResponse` | `AbilityComboStatisticResponse` |
| `findItemComboStatistics` | `findAbilityComboStatistics` |
| `findMatchesContainingAllItems` | `findMatchesContainingAllAbilities` |
| `ItemDomain` / `ItemDomain_` | `AbilityDomain` / `AbilityDomain_` |
| `itemRoot` | `abilityRoot` |
| `Set<Long> itemIds` | `Set<Long> abilityIds` |
| `ItemDomain_.itemId` | `AbilityDomain_.abilityId` |
| `ItemDomain_.itemName` | `AbilityDomain_.name` |
| `ItemDomain_.itemPrettyName` | `AbilityDomain_.prettyName` |
| `.itemId(...)`, `.itemName(...)`, `.itemPrettyName(...)` | `.abilityId(...)`, `.abilityName(...)`, `.abilityPrettyName(...)` |

Two deletions, because abilities have no purchase time and no neutral flag:

- Drop the `avgPurchaseTime` selection from the member query and the
  `.avgPurchaseTime(...)` builder call.
- Drop **both** `cb.equal(root.get(isNeutral), false)` predicates — `AbilityDomain`
  has no `isNeutral` field, so leaving them in will not compile.

Add these imports if absent:

```java
import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;
import java.util.LinkedHashSet;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.AbilityComboStatisticRepositoryTest"`

Expected: **PASS**, 5 tests.

- [ ] **Step 6: Expose it on the service**

Append to `server/src/main/java/com/ako/dbuff/service/ranking/AbilityRankingService.java`:

```java
  /**
   * Gets statistics over the games in which the player used every one of the named abilities.
   *
   * @throws UnknownConstantNameException if any supplied name matches no known constant
   */
  @Transactional(readOnly = true)
  public AbilityComboStatisticResponse getAbilityComboStatistics(
      Long playerId,
      LocalDate startDate,
      LocalDate endDate,
      Set<String> abilityNames,
      Set<String> heroNames) {

    Set<Long> abilityIds = resolveAbilitiesOrThrow(abilityNames);
    Set<Long> heroIds = resolveHeroesOrThrow(heroNames);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    log.info(
        "Fetching ability combo statistics for player {}: abilities={}, heroes={}, {} to {}",
        playerId,
        abilityIds,
        heroIds,
        startDate,
        effectiveEndDate);

    return abilityRankingRepository.findAbilityComboStatistics(
        playerId, abilityIds, heroIds, startDate, effectiveEndDate);
  }
```

Add `import com.ako.dbuff.resources.model.AbilityComboStatisticResponse;`.

- [ ] **Step 7: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/resources/model/AbilityComboStatisticResponse.java \
        server/src/main/java/com/ako/dbuff/dao/repo/AbilityRankingRepository.java \
        server/src/main/java/com/ako/dbuff/service/ranking/AbilityRankingService.java \
        server/src/test/java/com/ako/dbuff/dao/repo/AbilityComboStatisticRepositoryTest.java
git commit -m "feat: add conjunctive ability combo statistics query"
```

---

## Task 9: `discordUserId` on `PlayerDomain`

Links a Discord account to a Dota account so `/stats player:` can accept an
`@mention`. Consumed by plan 2.

> **Watch out:** `PlayerDomain`'s `@Id` is `name` (a String). The Dota account ID
> lives in a plain `id` column. Lookups and updates go through the *name* primary
> key, which is the opposite of what the field names suggest.

**Files:**
- Modify: `server/src/main/java/com/ako/dbuff/dao/model/PlayerDomain.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/repo/PlayerRepo.java`
- Test: `server/src/test/java/com/ako/dbuff/dao/repo/PlayerRepoDiscordLinkTest.java`

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/com/ako/dbuff/dao/repo/PlayerRepoDiscordLinkTest.java`:

```java
package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.PlayerDomain;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PlayerRepoDiscordLinkTest {

  @Autowired private PlayerRepo playerRepo;

  @BeforeEach
  void setUp() {
    playerRepo.save(
        PlayerDomain.builder().id(111L).name("Termit").discordUserId("discord-1").build());
    playerRepo.save(PlayerDomain.builder().id(222L).name("Unlinked").build());
  }

  @Test
  void findByDiscordUserId_returnsTheLinkedPlayer() {
    Optional<PlayerDomain> found = playerRepo.findByDiscordUserId("discord-1");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Termit");
    assertThat(found.get().getId()).isEqualTo(111L);
  }

  @Test
  void findByDiscordUserId_unknownId_isEmpty() {
    assertThat(playerRepo.findByDiscordUserId("nobody")).isEmpty();
  }

  @Test
  void discordUserId_isOptional() {
    Optional<PlayerDomain> unlinked = playerRepo.findById("Unlinked");

    assertThat(unlinked).isPresent();
    assertThat(unlinked.get().getDiscordUserId()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.PlayerRepoDiscordLinkTest"`

Expected: **compilation failure** — no `discordUserId` on the builder, no
`findByDiscordUserId`.

- [ ] **Step 3: Add the column**

In `server/src/main/java/com/ako/dbuff/dao/model/PlayerDomain.java`, add:

```java
  /**
   * Discord user ID (snowflake) linked to this player, or null when unlinked. Set via {@code /dbuff
   * link}. Stored as a String because Discord snowflakes exceed the range of a signed 64-bit int in
   * their string form and are always handled as strings by JDA.
   */
  @Column(name = "discord_user_id")
  private String discordUserId;
```

Confirm `jakarta.persistence.Column` is imported.

- [ ] **Step 4: Add the finder**

In `server/src/main/java/com/ako/dbuff/dao/repo/PlayerRepo.java`, add:

```java
  /**
   * Finds the player linked to a Discord user.
   *
   * @param discordUserId the Discord user snowflake
   * @return the linked player, if any
   */
  Optional<PlayerDomain> findByDiscordUserId(String discordUserId);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "com.ako.dbuff.dao.repo.PlayerRepoDiscordLinkTest"`

Expected: **PASS**, 3 tests.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add server/src/main/java/com/ako/dbuff/dao/model/PlayerDomain.java \
        server/src/main/java/com/ako/dbuff/dao/repo/PlayerRepo.java \
        server/src/test/java/com/ako/dbuff/dao/repo/PlayerRepoDiscordLinkTest.java
git commit -m "feat: link Discord users to players via discordUserId"
```

---

## Task 10: Declare the missing indexes

`server/src/main/resources/db/migration/V2__item_ranking_indexes.sql` targets
exactly these queries and **has never run** — Flyway is not on the classpath, so
none of those indexes exist in any deployed database. The cross-joins in these
repositories are unindexed today on a 2 GiB instance sharing RAM with the JVM and
Postgres.

Declaring them via `@Index` keeps Hibernate as the single schema owner. This
deliberately drops the `INCLUDE` and partial-index variants from the V2 script,
which JPA cannot express; those remain a `psql`-only follow-up if profiling shows
the plain composites are insufficient.

**Files:**
- Modify: `server/src/main/java/com/ako/dbuff/dao/model/ItemDomain.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/model/AbilityDomain.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/model/PlayerMatchStatisticDomain.java`
- Modify: `server/src/main/java/com/ako/dbuff/dao/model/MatchDomain.java`
- Modify: `server/src/main/resources/db/migration/V2__item_ranking_indexes.sql`

- [ ] **Step 1: Annotate `ItemDomain`**

Add above the class, merging with any existing `@Table`:

```java
@Table(
    name = "item_domain",
    indexes = {
      @Index(name = "idx_item_domain_player_match_slot", columnList = "playerId, matchId, playerSlot, isNeutral"),
      @Index(name = "idx_item_domain_item_id", columnList = "itemId")
    })
```

Imports: `jakarta.persistence.Index`, `jakarta.persistence.Table`.

> `columnList` uses **entity property names**, which Hibernate maps to the physical
> column names. Verify the generated DDL in step 5 rather than assuming.

- [ ] **Step 2: Annotate `AbilityDomain`**

```java
@Table(
    name = "ability_domain",
    indexes = {
      @Index(name = "idx_ability_domain_player_match_slot", columnList = "playerId, matchId, playerSlot"),
      @Index(name = "idx_ability_domain_ability_id", columnList = "abilityId")
    })
```

- [ ] **Step 3: Annotate `PlayerMatchStatisticDomain`**

```java
@Table(
    name = "player_match_statistic_domain",
    indexes = {
      @Index(name = "idx_player_match_stat_player_match", columnList = "playerId, matchId"),
      @Index(name = "idx_player_match_stat_player_hero", columnList = "playerId, heroId"),
      @Index(name = "idx_player_match_stat_match_id", columnList = "matchId")
    })
```

The `playerId, heroId` index is what the new hero filter needs.

- [ ] **Step 4: Annotate `MatchDomain`**

Add to its existing `@Table`:

```java
      @Index(name = "idx_match_domain_start_local_date", columnList = "startLocalDate")
```

- [ ] **Step 5: Verify the generated DDL**

```bash
docker-compose up -d
./gradlew :server:bootRun
```

In another shell:

```bash
docker-compose exec postgres psql -U postgres -d dbuff -c "\di idx_*"
```

Expected: all eight indexes listed. If any are missing, `ddl-auto=update` did not
create them — its index handling is less reliable than its column handling. In
that case create them by hand and record the exact SQL in the file edited in
step 6:

```bash
docker-compose exec postgres psql -U postgres -d dbuff \
  -c "CREATE INDEX IF NOT EXISTS idx_player_match_stat_player_hero ON player_match_statistic_domain (player_id, hero_id);"
```

- [ ] **Step 6: Mark the dead migration script as dead**

Prepend to `server/src/main/resources/db/migration/V2__item_ranking_indexes.sql`:

```sql
-- ============================================================================
-- NOT EXECUTED. Flyway is not on the classpath, so nothing in db/migration has
-- ever run against any database. Retained only as documentation of the intended
-- INCLUDE and partial indexes, which JPA cannot express.
--
-- The composite indexes below are now declared via @Index on ItemDomain,
-- AbilityDomain, PlayerMatchStatisticDomain and MatchDomain, and are created by
-- Hibernate's ddl-auto=update. The INCLUDE / WHERE variants here are NOT
-- created; apply them by hand via psql only if profiling shows the plain
-- composites are insufficient.
-- ============================================================================
```

- [ ] **Step 7: Confirm production instructions**

Note in the commit message that this task changes the deployed schema on next
release, and that the index build locks each table briefly. On a 2 GiB instance
with a small dataset this is seconds, not minutes — but confirm the row counts
first:

```bash
docker-compose exec postgres psql -U postgres -d dbuff \
  -c "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10;"
```

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
./gradlew test
git add server/src/main/java/com/ako/dbuff/dao/model/ \
        server/src/main/resources/db/migration/V2__item_ranking_indexes.sql
git commit -m "perf: declare ranking query indexes via @Index

The V2 migration script was never executed because Flyway is not on the
classpath, so these indexes have never existed in any deployed database.
Declaring them on the entities lets ddl-auto=update create them and keeps
Hibernate as the single schema owner. Adds a table lock during index build
on next deploy."
```

---

## Plan Self-Review

Checked against the spec's "Data Layer Changes" section:

| Spec requirement | Task |
|---|---|
| Hero filter on the three query methods | 3, 4, 5 |
| `getTotalMatchCount` must also receive the hero filter | 3 step 4b/4e, 4 step 4b/4e — with a dedicated regression test in each |
| `popularHeroes` omitted when hero-filtered | 5 — `heroFiltered` flag; the formatter honours it in plan 2 |
| Aggregate average use count | 3, 4 |
| Null `avgUseCount` means "no data", not zero | 3 step 3, 4 step 3 — asserted in 3 |
| Conjunctive combo queries | 7, 8 |
| `COUNT(DISTINCT …)` not `COUNT(*)` | 7 — asserted by `duplicateRowsForOneItemDoNotSatisfyATwoItemQuery` |
| Single item degenerates cleanly to n=1 | 7 — asserted by `singleItemDegeneratesToGamesWithThatItem` |
| `discordUserId` on `PlayerDomain` | 9 |
| `StatsPeriod` enum with patch fallback | 1 |
| Indexes via `@Index`, Flyway rejected | 10 |
| Name resolution reports rather than drops | 2, 6 |

**Deliberately deferred to plan 2**, since they are Discord-surface concerns:
`limit:` capping at 25, the `players:` cap of 5, "did you mean" rendering, and
progressive per-player posting.

**Not covered by either plan, and out of scope per the spec:** exposing `heroes`
as a REST query parameter. Tasks 3–6 pass `null` from the existing controllers,
so the HTTP contract is unchanged.

**Type consistency check:** `findItemRankingsByPlayer` and
`findAbilityRankingsByPlayer` take `heroIds` as the 6th of 7 parameters
throughout. `findPlayerStatistics` takes `heroIds` as the 5th of 5.
`ItemComboStatisticResponse.Member` uses `itemId`/`itemName`/`itemPrettyName`;
`AbilityComboStatisticResponse.Member` uses
`abilityId`/`abilityName`/`abilityPrettyName`. `NameResolution` exposes
`resolvedIds()`, `unresolvedNames()`, `hasUnresolved()`, `idsOrNullIfEmpty()` and
is used with those names in Tasks 3–8.

**Known ordering constraint:** Task 6 depends on Tasks 3 and 4 for the 7-argument
repository signatures, and on Task 5 for `PlayerStatisticService`. Executing 1→10
in order satisfies this.



