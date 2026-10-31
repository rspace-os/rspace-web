package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstrumentQueryCountMVCIT extends SpringTransactionalTest {
  private final ApiV2ResourceCatalog catalog;
  private final InstrumentEntityApiManager instruments;

  @Autowired
  InstrumentQueryCountMVCIT(ApiV2ResourceCatalog catalog, InstrumentEntityApiManager instruments) {
    this.catalog = catalog;
    this.instruments = instruments;
  }

  @Test
  void instrumentSliceNavigationAndOptInCount() throws Exception {
    var owner = createInitAndLoginAnyUser();
    for (int i = 0; i < 5; i++) createBasicInstrumentForUser(owner, "Slice scope " + i);
    sessionFactory.getCurrentSession().flush();
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    for (int page : List.of(1, 2, 3, 4, Integer.MAX_VALUE)) {
      var request =
          get("/api/v2/instruments")
              .param("where", "name=contains='Slice scope'")
              .param("limit", "2")
              .param("page", Integer.toString(page))
              .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner));
      mvc.perform(request)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").doesNotExist())
          .andExpect(jsonPath("$.totalPages").doesNotExist())
          .andExpect(jsonPath("$.hasNextPage").value(page < 3))
          .andExpect(jsonPath("$.hasPrevPage").value(page > 1));
      mvc.perform(request.param("totals", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(5))
          .andExpect(jsonPath("$.totalPages").value(3))
          .andExpect(jsonPath("$.hasNextPage").value(page < 3))
          .andExpect(jsonPath("$.hasPrevPage").value(page > 1 && page <= 3));
    }
  }

  @Test
  void listQueryCountDoesNotGrowWithPageSizeAtAnyDepth() throws Exception {
    var owner = createInitAndLoginAnyUser();
    for (int i = 0; i < 5; i++) {
      var container = createBasicContainerForUser(owner, "Query parent " + i, List.of());
      var item = createBasicInstrumentForUser(owner, "Query instrument " + i);
      instruments
          .assertUserCanReadInstrument(item.getId(), owner)
          .moveToNewParent(containerDao.get(container.getId()));
    }
    sessionFactory.getCurrentSession().flush();
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    var stats = sessionFactory.getStatistics();
    boolean enabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    long[][] counts = new long[3][2];
    try {
      for (int depth = 0; depth <= 2; depth++) {
        for (int index = 0; index < 2; index++) {
          int size = index == 0 ? 1 : 5;
          sessionFactory.getCurrentSession().clear();
          sessionFactory.getCache().evictAllRegions();
          stats.clear();
          mvc.perform(
                  get("/api/v2/instruments")
                      .param("where", "name=contains='Query instrument'")
                      .param("limit", Integer.toString(size))
                      .param("depth", Integer.toString(depth))
                      .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.docs.length()").value(size));
          counts[depth][index] = stats.getPrepareStatementCount();
          assertEquals(
              0,
              stats
                  .getEntityStatistics("com.researchspace.model.inventory.Instrument")
                  .getLoadCount());
          assertEquals(
              0,
              stats
                  .getEntityStatistics("com.researchspace.model.inventory.Container")
                  .getLoadCount());
        }
        System.out.printf(
            "INSTRUMENT_QUERIES depth=%d page1=%d page5=%d%n",
            depth, counts[depth][0], counts[depth][1]);
      }
      for (int depth = 0; depth <= 2; depth++) {
        assertEquals(counts[depth][0], counts[depth][1], "SQL grows at depth " + depth);
      }
    } finally {
      stats.setStatisticsEnabled(enabled);
    }
  }
}
