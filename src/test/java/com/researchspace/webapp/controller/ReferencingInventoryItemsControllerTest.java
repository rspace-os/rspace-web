package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferencingInventoryItemsControllerTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  @Mock private UserManager userManager;
  @InjectMocks private ReferencingInventoryItemsController controller;

  @Test
  void resolvesSessionUserAndDelegatesToManager() {
    User user = new User("bob");
    when(userManager.getUserByUsername("bob")).thenReturn(user);
    List<ApiInventoryReferencingItem> rows = List.of(new ApiInventoryReferencingItem());
    when(inventoryLinkManager.findReferencingItems("SD123", user)).thenReturn(rows);
    Principal principal = () -> "bob";

    ApiInventoryReferencingItems result =
        controller.getReferencingInventoryItems("SD123", principal);

    assertSame(rows, result.getReferencingItems());
    verify(inventoryLinkManager).findReferencingItems(eq("SD123"), eq(user));
  }

  @Test
  void passesElnNotebookGlobalIdThrough() {
    User user = new User("alice");
    when(userManager.getUserByUsername("alice")).thenReturn(user);
    when(inventoryLinkManager.findReferencingItems("NB9", user)).thenReturn(List.of());
    Principal principal = () -> "alice";

    controller.getReferencingInventoryItems("NB9", principal);

    verify(inventoryLinkManager).findReferencingItems("NB9", user);
  }
}
