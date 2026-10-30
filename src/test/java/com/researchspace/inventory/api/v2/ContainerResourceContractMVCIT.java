package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.service.inventory.ContainerReadManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContainerResourceContractMVCIT extends SpringTransactionalTest {
  private final ContainerReadManager reads;
  private final ContainerReadAccess access;

  @Autowired
  ContainerResourceContractMVCIT(ContainerReadManager reads, ContainerReadAccess access) {
    this.reads = reads;
    this.access = access;
  }

  @Test
  void listsAndReadsOnlyVisibleContainers() throws Exception {
    var owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    var outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(owner, outsider);
    var container = createBasicContainerForUser(owner, "Visible container", List.of());
    var operations = new ContainerResourceOperations(reads, access);
    var catalog = new ApiV2ResourceCatalog(List.of(operations.containerApiV2Resource()));
    var mvc = MockMvcBuilders.standaloneSetup(new ApiV2CrudController(catalog)).build();
    mvc.perform(
            get("/api/v2/containers")
                .param("where", "id==" + container.getId())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].name").value("Visible container"));
    mvc.perform(
            get("/api/v2/containers/" + container.getId())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(container.getId()));
    mvc.perform(
            get("/api/v2/containers")
                .param("where", "id==" + container.getId())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty());
    assertTrue(operations.findById(container.getId(), outsider).isEmpty());
  }
}
