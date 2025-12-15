/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryIdentifiersApiController.ApiInventoryIdentifierPost;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.model.User;
import java.util.List;
import javax.naming.InvalidNameException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @since 1.91
 */
@RequestMapping("/api/inventory/v1/identifiers")
public interface InventoryIdentifiersApi {

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  List<ApiInventoryDOI> getUserIdentifiers(
      String state, Boolean isAssociated, String identifier, User user) throws InvalidNameException;

  /** Register new IGSN identifier for record with given globalId */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiInventoryDOI registerNewIdentifier(ApiInventoryIdentifierPost parentGlobalId, User user);

  /** Allocate a number of IGSN not associated with any globalId */
  @PostMapping(value = "/bulk/{count}")
  @ResponseStatus(HttpStatus.CREATED)
  List<ApiInventoryDOI> bulkAllocateIdentifiers(Integer count, User user);

  /**
   * Assign an existing IGSN (identifierId) to a specific inventory item (parentGlobalId)
   *
   * @param identifierId
   * @param parentGlobalId
   * @param user
   * @return
   */
  @PostMapping(value = "/{identifierId}/assign")
  ApiInventoryDOI assignIdentifier(
      Long identifierId, ApiInventoryIdentifierPost parentGlobalId, User user);

  @DeleteMapping(value = "/{identifierId}")
  boolean deleteIdentifier(Long identifierId, User user);

  /** Publish IGSN identifier of record with given globalId */
  @PostMapping(value = "/{identifierId}/publish")
  ApiInventoryDOI publishIdentifier(Long identifierId, User user);

  /** Retract IGSN identifier of record with given globalId */
  @PostMapping(value = "/{identifierId}/retract")
  ApiInventoryDOI retractIdentifier(Long identifierId, User user);

  @GetMapping(value = "/testDataCiteConnection")
  boolean testDataCiteConnection(User user);
}
