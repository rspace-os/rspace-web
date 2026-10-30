package com.researchspace.inventory.api.v2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CollectionBoundedCountMVCIT extends SpringTransactionalTest {
  @Autowired private ApiV2ResourceCatalog catalog;

  @Test
  void validatesCountBoundsOnBothRoutes() throws Exception {
    var owner = createInitAndLoginAnyUser();
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    for (String route : java.util.List.of("/api/v2/instruments", "/api/v2/instruments/count")) {
      for (String max : java.util.List.of("0", "10001")) {
        mvc.perform(
                get(route)
                    .param("totals", "true")
                    .param("max", max)
                    .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
            .andExpect(status().isBadRequest());
      }
    }
  }

  @Test
  void countedUserPagesCapOnlyTheMetadata() throws Exception {
    var admin = createAndSaveSysadminUser();
    createInitAndLoginAnyUser();
    createInitAndLoginAnyUser();
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    mvc.perform(
            get("/api/v2/users")
                .param("totals", "true")
                .param("max", "1")
                .param("limit", "1")
                .param("page", "2")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1))
        .andExpect(jsonPath("$.totalIsLowerBound").value(true))
        .andExpect(jsonPath("$.totalPages").doesNotExist())
        .andExpect(jsonPath("$.hasNextPage").value(true))
        .andExpect(jsonPath("$.hasPrevPage").value(true));
  }

  @Test
  void exactAndCappedCountsKeepNavigationPastTheCap() throws Exception {
    var owner = createInitAndLoginAnyUser();
    for (int i = 0; i < 5; i++) createBasicInstrumentForUser(owner, "Bounded count " + i);
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    for (int max : java.util.List.of(4, 5)) {
      mvc.perform(
              get("/api/v2/instruments/count")
                  .param("where", "name=contains='Bounded count'")
                  .param("max", Integer.toString(max))
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(max))
          .andExpect(jsonPath("$.totalIsLowerBound").value(max < 5));
      var result =
          mvc.perform(
                  get("/api/v2/instruments")
                      .param("where", "name=contains='Bounded count'")
                      .param("totals", "true")
                      .param("max", Integer.toString(max))
                      .param("limit", "1")
                      .param("page", "5")
                      .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.totalDocs").value(max))
              .andExpect(jsonPath("$.totalIsLowerBound").value(max < 5))
              .andExpect(jsonPath("$.hasPrevPage").value(true));
      if (max < 5) result.andExpect(jsonPath("$.totalPages").doesNotExist());
      else result.andExpect(jsonPath("$.totalPages").value(5));
    }
    mvc.perform(
            get("/api/v2/instruments")
                .param("where", "name=contains='Bounded count'")
                .param("totals", "true")
                .param("max", "2")
                .param("limit", "1")
                .param("page", "3")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNextPage").value(true));
  }
}
