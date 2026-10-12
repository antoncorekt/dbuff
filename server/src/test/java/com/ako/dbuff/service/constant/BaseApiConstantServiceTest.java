package com.ako.dbuff.service.constant;

import com.ako.dbuff.dao.model.ConstantDomain;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseApiConstantServiceTest {

  private static final String ATTACK_TYPE = "Melee";

  private final ConstantDomainRepo repo = mock(ConstantDomainRepo.class);
  private final ConstantFetcherService fetcher = mock(ConstantFetcherService.class);
  private final HeroConstantService service = new HeroConstantService(repo, fetcher);

  @Test
  void storedRowWithoutVersion_isRefetchedAndStoredWithCurrentVersion() {
    when(repo.findById("heroes")).thenReturn(Optional.of(stored(null)));
    GetConstantsByResource200Response fresh = fresh();
    when(fetcher.fetch(any())).thenReturn(fresh);

    Map<String, HeroConstant> result = service.getConstantMap();

    assertEquals(ATTACK_TYPE, result.get("1").getAttack_type());
    ArgumentCaptor<ConstantDomain> saved = ArgumentCaptor.forClass(ConstantDomain.class);
    verify(repo).save(saved.capture());
    assertEquals(1, saved.getValue().getSchemaVersion());
  }

  @Test
  void storedRowWithCurrentVersion_isNotRefetched() {
    when(repo.findById("heroes")).thenReturn(Optional.of(stored(1)));

    Map<String, HeroConstant> result = service.getConstantMap();

    assertNull(result.get("1").getAttack_type());
    verify(fetcher, never()).fetch(any());
    verify(repo, never()).save(any());
  }

  @Test
  void failedRefetch_keepsServingTheStoredRow() {
    when(repo.findById("heroes")).thenReturn(Optional.of(stored(null)));
    when(fetcher.fetch(any())).thenThrow(new RuntimeException("down"));

    Map<String, HeroConstant> result = service.getConstantMap();
    service.getConstantMap();

    assertEquals("Anti-Mage", result.get("1").getLocalized_name());
    verify(repo, never()).save(any());
  }

  private static ConstantDomain stored(Integer version) {
    return ConstantDomain.builder()
        .constantName("heroes")
        .constantValue(
            Map.of(
                "1",
                Map.of("id", "1", "name", "npc_dota_hero_antimage", "localized_name", "Anti-Mage")))
        .schemaVersion(version)
        .build();
  }

  private static GetConstantsByResource200Response fresh() {
    GetConstantsByResource200Response response = mock(GetConstantsByResource200Response.class);
    when(response.getMapStringObject())
        .thenReturn(
            Map.of(
                "1",
                Map.of(
                    "id",
                    1,
                    "name",
                    "npc_dota_hero_antimage",
                    "localized_name",
                    "Anti-Mage",
                    "attack_type",
                    ATTACK_TYPE)));
    return response;
  }
}
