package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.service.ResourceMutationManager;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.service.inventory.ContainerReadManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContainerResourceContractMVCIT extends SpringTransactionalTest {
  private final ResourceMutationManager mutations;
  private final ApiV2ResourceCatalog catalog;
  private final ContainerReadManager reads;
  private final ContainerReadAccess access;
  private final com.researchspace.service.inventory.InstrumentEntityApiManager instruments;

  @Autowired
  ContainerResourceContractMVCIT(
      ResourceMutationManager mutations,
      ApiV2ResourceCatalog catalog,
      ContainerReadManager reads,
      ContainerReadAccess access,
      com.researchspace.service.inventory.InstrumentEntityApiManager instruments) {
    this.mutations = mutations;
    this.catalog = catalog;
    this.reads = reads;
    this.access = access;
    this.instruments = instruments;
  }

  @Test
  void startsTheScopeCheckAndMutationTransaction() {
    org.springframework.test.context.transaction.TestTransaction.end();
    assertFalse(
        org.springframework.transaction.support.TransactionSynchronizationManager
            .isActualTransactionActive());
    assertTrue(
        mutations.execute(
            org.springframework.transaction.support.TransactionSynchronizationManager
                ::isActualTransactionActive));
    assertFalse(
        org.springframework.transaction.support.TransactionSynchronizationManager
            .isActualTransactionActive());
    org.springframework.test.context.transaction.TestTransaction.start();
  }

  @Test
  void listsAndReadsOnlyVisibleContainers() throws Exception {
    var owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    var outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(owner, outsider);
    var container = createBasicContainerForUser(owner, "Visible container", List.of());
    var operations = new ContainerResourceOperations(reads, access);
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
    mvc.perform(
            get("/api/v2/containers/count")
                .param("where", "id==" + container.getId())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));
    mvc.perform(
            get("/api/v2/containers/count")
                .param("where", "id==" + container.getId())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
    assertTrue(operations.findById(container.getId(), outsider).isEmpty());
    var item = createBasicInstrumentForUser(owner, "Scope");
    var entity = instruments.assertUserCanReadInstrument(item.getId(), owner);
    entity.moveToNewParent(containerDao.get(container.getId()));
    sessionFactory.getCurrentSession().flush();
    mvc.perform(
            get("/api/v2/instruments/" + item.getId())
                .param("depth", "2")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentContainer.relationTo").value("containers"))
        .andExpect(jsonPath("$.parentContainer.globalId").value("IC" + container.getId()))
        .andExpect(jsonPath("$.parentContainer.value.name").value("Visible container"));
    var parent = containerDao.get(container.getId());
    parent.setRecordDeleted(true);
    sessionFactory.getCurrentSession().flush();
    for (int depth : List.of(0, 1, 2)) {
      mvc.perform(
              get("/api/v2/instruments/" + item.getId())
                  .param("depth", Integer.toString(depth))
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(owner)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.parentContainer").doesNotExist());
    }
  }
}
