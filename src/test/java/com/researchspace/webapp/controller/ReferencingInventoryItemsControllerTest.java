package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReferencingInventoryItemsControllerTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  @Mock private InventoryFileApiManager inventoryFileApiManager;
  @Mock private UserManager userManager;
  @Mock private MessageSourceUtils messageSource;
  @InjectMocks private ReferencingInventoryItemsController controller;

  @Test
  void resolvesSessionUserAndDelegatesToManager() {
    User user = new User("bob");
    when(userManager.getUserByUsername("bob")).thenReturn(user);
    List<ApiInventoryReferencingItem> rows = List.of(new ApiInventoryReferencingItem());
    when(inventoryLinkManager.findReferencingItems("SD123", user)).thenReturn(rows);
    Principal principal = () -> "bob";

    ResponseEntity<?> response = controller.getReferencingInventoryItems("SD123", principal);

    assertSame(rows, ((ApiInventoryReferencingItems) response.getBody()).getReferencingItems());
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

  @Test
  void malformedGlobalIdReturnsJsonNotFoundInsteadOfEscaping() {
    // findReferencingItems throws ApiRuntimeException for a malformed globalId; no
    // @ControllerAdvice
    // applies to this @Controller, so it must translate the error to a structured JSON 4xx itself
    // rather than letting it escape as an HTML 500.
    User user = new User("bob");
    when(userManager.getUserByUsername("bob")).thenReturn(user);
    when(inventoryLinkManager.findReferencingItems("bad!", user))
        .thenThrow(new ApiRuntimeException("errors.inventory.field.link.targetNotFound", "bad!"));
    when(messageSource.getMessage(eq("errors.inventory.field.link.targetNotFound"), any()))
        .thenReturn("bad! does not exist, or you do not have permission to view it.");
    Principal principal = () -> "bob";

    ResponseEntity<?> response = controller.getReferencingInventoryItems("bad!", principal);

    assertEquals(404, response.getStatusCode().value());
    assertTrue(response.getBody() instanceof ErrorList);
  }

  @Test
  void attachingEndpointDelegatesToFileManager() {
    User user = new User("bob");
    when(userManager.getUserByUsername("bob")).thenReturn(user);
    List<ApiInventoryReferencingItem> rows = List.of(new ApiInventoryReferencingItem());
    when(inventoryFileApiManager.findAttachingItems("GL5", user)).thenReturn(rows);
    Principal principal = () -> "bob";

    ResponseEntity<?> response = controller.getAttachingInventoryItems("GL5", principal);

    assertSame(rows, ((ApiInventoryReferencingItems) response.getBody()).getReferencingItems());
    verify(inventoryFileApiManager).findAttachingItems(eq("GL5"), eq(user));
  }

  @Test
  void attachingEndpointMalformedGlobalIdReturnsJsonNotFound() {
    // the file manager throws ApiRuntimeException for a bad/non-gallery id; this @Controller has no
    // @ControllerAdvice, so it must translate that to a JSON 404 rather than an HTML 500
    User user = new User("bob");
    when(userManager.getUserByUsername("bob")).thenReturn(user);
    when(inventoryFileApiManager.findAttachingItems("bad!", user))
        .thenThrow(new ApiRuntimeException("errors.inventory.field.link.targetNotFound", "bad!"));
    when(messageSource.getMessage(eq("errors.inventory.field.link.targetNotFound"), any()))
        .thenReturn("bad! does not exist, or you do not have permission to view it.");
    Principal principal = () -> "bob";

    ResponseEntity<?> response = controller.getAttachingInventoryItems("bad!", principal);

    assertEquals(404, response.getStatusCode().value());
    assertTrue(response.getBody() instanceof ErrorList);
  }
}
