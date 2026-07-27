package com.researchspace.webapp.controller;

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
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Session-authenticated lookup of the Inventory items connected to a given ELN record. Backs the
 * ELN-side "Related inventory items" panels (the legacy record info panel for documents/notebooks
 * and the React gallery info panel). Those run in the browser session and do not present the API
 * key or OAuth bearer token that the {@code /api/inventory/v1} endpoints require, so they call
 * these {@code /workspace} endpoints instead. Both delegate to the same permission-filtered
 * managers as the API endpoints, so callers only ever see items they may read.
 *
 * <p>Two relation kinds are served separately, one endpoint each: <em>links</em> (an inventory Link
 * field pointing at the record) and <em>attachments</em> (an inventory item that attached a Gallery
 * file). The Gallery info panel calls both and merges them into one grid.
 */
@Controller
@RequestMapping("/workspace")
public class ReferencingInventoryItemsController {

  @Autowired private InventoryLinkManager inventoryLinkManager;
  @Autowired private InventoryFileApiManager inventoryFileApiManager;
  @Autowired private UserManager userManager;
  @Autowired private MessageSourceUtils messageSource;

  @ResponseBody
  @GetMapping("/getReferencingInventoryItems/{globalId}")
  public ResponseEntity<?> getReferencingInventoryItems(
      @PathVariable("globalId") String globalId, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    return buildResponse(() -> inventoryLinkManager.findReferencingItems(globalId, user));
  }

  @ResponseBody
  @GetMapping("/getAttachingInventoryItems/{globalId}")
  public ResponseEntity<?> getAttachingInventoryItems(
      @PathVariable("globalId") String globalId, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    return buildResponse(() -> inventoryFileApiManager.findAttachingItems(globalId, user));
  }

  /**
   * Runs a referencing-items lookup and wraps the result, translating the manager's malformed- or
   * unreadable-target {@link ApiRuntimeException} into a structured JSON 404. No
   * {@code @ControllerAdvice} applies to this session {@code @Controller} and it does not extend
   * {@code BaseController}, so without this the exception would escape to Spring's default
   * dispatcher as an HTML 500. Translating it keeps this endpoint's contract JSON, matching the
   * api.v1 referencing-items endpoint.
   */
  private ResponseEntity<?> buildResponse(Supplier<List<ApiInventoryReferencingItem>> lookup) {
    try {
      ApiInventoryReferencingItems result = new ApiInventoryReferencingItems();
      result.setReferencingItems(lookup.get());
      return ResponseEntity.ok(result);
    } catch (ApiRuntimeException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ErrorList.of(messageSource.getMessage(e.getErrorCode(), e.getArgs())));
    }
  }
}
