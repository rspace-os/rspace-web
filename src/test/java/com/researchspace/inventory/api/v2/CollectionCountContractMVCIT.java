package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CollectionCountContractMVCIT extends SpringTransactionalTest {
  @Autowired private ApiV2ResourceCatalog catalog;

  @Test
  void countAndRequestedTotalAgreeForEveryRegisteredResourceAndCaller() throws Exception {
    var owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Count contract");
    var other = createInitAndLoginAnyUser();
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    var mapper = new ObjectMapper();
    for (var caller : java.util.List.of(owner, other)) {
      for (var resource : catalog.routableResources()) {
        if (!resource.supports(ResourceOperation.COUNT)
            || !resource.supports(ResourceOperation.LIST)) continue;
        for (String where : java.util.List.of("", "id=gt=0")) {
          var count =
              mvc.perform(
                      get("/api/v2/" + resource.resourceName() + "/count")
                          .param("where", where)
                          .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(caller)))
                  .andExpect(status().isOk())
                  .andReturn();
          var list =
              mvc.perform(
                      get("/api/v2/" + resource.resourceName())
                          .param("where", where)
                          .param("totals", "true")
                          .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(caller)))
                  .andExpect(status().isOk())
                  .andReturn();
          assertEquals(
              mapper.readTree(count.getResponse().getContentAsString()).get("totalDocs"),
              mapper.readTree(list.getResponse().getContentAsString()).get("totalDocs"),
              resource.resourceName() + " caller=" + caller.getUsername());
        }
      }
    }
  }
}
