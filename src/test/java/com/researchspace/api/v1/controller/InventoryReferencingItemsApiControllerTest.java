package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryLinkManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryReferencingItemsApiControllerTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  @InjectMocks private InventoryReferencingItemsApiController controller;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("viewer");
  }

  @Test
  void genericEndpointDelegatesWithRawGlobalId() {
    List<ApiInventoryReferencingItem> rows = List.of(new ApiInventoryReferencingItem());
    when(inventoryLinkManager.findReferencingItems("SD123", user)).thenReturn(rows);

    assertSame(
        rows, controller.getReferencingItemsForGlobalId("SD123", user).getReferencingItems());
  }

  @Test
  void genericEndpointPassesElnGlobalIdThrough() {
    when(inventoryLinkManager.findReferencingItems("NB9", user)).thenReturn(List.of());

    controller.getReferencingItemsForGlobalId("NB9", user);

    verify(inventoryLinkManager).findReferencingItems("NB9", user);
  }

  @Test
  void typedSampleEndpointDelegatesWithSamplePrefixedGlobalId() {
    when(inventoryLinkManager.findReferencingItems("SA42", user)).thenReturn(List.of());

    controller.getReferencingItemsForSample(42L, user);

    verify(inventoryLinkManager).findReferencingItems("SA42", user);
  }

  @Test
  void wrapsManagerResultInResponseObject() {
    when(inventoryLinkManager.findReferencingItems("SD1", user)).thenReturn(List.of());

    assertEquals(
        0, controller.getReferencingItemsForGlobalId("SD1", user).getReferencingItems().size());
  }
}
