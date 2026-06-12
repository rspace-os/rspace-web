package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryLinkManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryLinkTargetApiControllerTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  @InjectMocks private InventoryLinkTargetApiController controller;

  @Test
  void returnsTheManagerResolvedSummaryForTheRequestedGlobalId() {
    User user = new User("any");
    ApiInventoryLinkTargetSummary summary = new ApiInventoryLinkTargetSummary();
    summary.setGlobalId("SA42");
    summary.setDeleted(true);
    when(inventoryLinkManager.getTargetSummary("SA42", user)).thenReturn(summary);

    assertSame(summary, controller.getLinkTargetSummary("SA42", user));
  }
}
