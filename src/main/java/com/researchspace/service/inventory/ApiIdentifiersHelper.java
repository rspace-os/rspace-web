package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.properties.IPropertyHolder;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * To deal with API identifiers conversion when doi identifiers are sent between RSpace server and
 * API client.
 */
@Component
public class ApiIdentifiersHelper {

  private @Autowired IPropertyHolder properties;
  private @Autowired DigitalObjectIdentifierDao doiDao;

  public boolean createDeleteRequestedIdentifiers(
      List<ApiInventoryDOI> incomingIdentifiers, InventoryRecord parentInvRec, User user) {

    boolean changed = false;
    if (!CollectionUtils.isEmpty(incomingIdentifiers)) {
      for (ApiInventoryDOI apiIdentifier : incomingIdentifiers) {
        if (apiIdentifier.isRegisterIdentifierRequest()) {
          addRecordIdentifierForRegisteredApiIdentifier(apiIdentifier, parentInvRec);
          changed = true;
        }
        if (apiIdentifier.isDeleteIdentifierRequest()) {
          if (apiIdentifier.getId() == null) {
            throw new IllegalArgumentException(
                "'id' property not provided " + "for DOI with 'deleteIdentifierRequest' flag");
          }
          Optional<DigitalObjectIdentifier> dbIdentifier =
              parentInvRec.getActiveIdentifiers().stream()
                  .filter(doi -> apiIdentifier.getId().equals(doi.getId()))
                  .findFirst();
          if (!dbIdentifier.isPresent()) {
            throw new IllegalArgumentException(
                "DOI with id: "
                    + apiIdentifier.getDoi()
                    + " doesn't match id of any pre-existing identifiers");
          }
          dbIdentifier.get().setDeleted(true);
          changed = true;
        }
      }
    }
    if (changed) {
      parentInvRec.refreshActiveIdentifiers();
    }
    return changed;
  }

  /**
   * Records this RSpace's own public landing page for the identifier as its LOCAL_URL, built from
   * whichever suffix the entity ended up with: the one pre-generated on the DTO before the provider
   * was called, or the entity's own when the caller carried none (RSDEV-1254, ADR 0006).
   *
   * <p>Built through {@link ApiInventoryDOI#publicLandingPageUrl}, the same builder the registered
   * address goes through, so the stored and the registered address cannot be normalised
   * differently. With no server URL configured the property is left unset rather than stored as the
   * literal "null/public/inventory/...", which would then surface as the identifier's {@code url}
   * over the API; a missing value can still be filled in later.
   */
  private void addPublicLandingPageUrl(DigitalObjectIdentifier newDoi) {
    ApiInventoryDOI.publicLandingPageUrl(properties.getServerUrl(), newDoi.getPublicLink())
        .ifPresent(
            url ->
                newDoi.addOtherData(
                    DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL, url));
  }

  private void addRecordIdentifierForRegisteredApiIdentifier(
      ApiInventoryDOI apiIdentifier, InventoryRecord parentInvRec) {
    DigitalObjectIdentifier newDoi =
        new DigitalObjectIdentifier(null, null, apiIdentifier.getPublicLinkSuffix());
    addPublicLandingPageUrl(newDoi);
    newDoi.setOwner(parentInvRec.getOwner());
    apiIdentifier.applyChangesToDatabaseDOI(newDoi);
    parentInvRec.addIdentifier(newDoi);
  }

  private void addRecordIdentifierForAssignApiIdentifier(
      ApiInventoryDOI apiIdentifier, InventoryRecord parentInvRec) {
    DigitalObjectIdentifier existingDoi = doiDao.get(apiIdentifier.getId());
    existingDoi.setOwner(parentInvRec.getOwner());
    apiIdentifier.applyChangesToDatabaseDOI(existingDoi);
    parentInvRec.addIdentifier(existingDoi);
  }

  public DigitalObjectIdentifier createDoiToSave(ApiInventoryDOI apiIdentifier, User creator) {
    DigitalObjectIdentifier newDoi =
        new DigitalObjectIdentifier(null, null, apiIdentifier.getPublicLinkSuffix());
    addPublicLandingPageUrl(newDoi);
    newDoi.setOwner(creator);
    apiIdentifier.applyChangesToDatabaseDOI(newDoi);
    return newDoi;
  }

  public boolean createAssignRequestedIdentifiers(
      List<ApiInventoryDOI> incomingIdentifiers, InventoryRecord parentInvRec, User user) {
    boolean changed = false;
    if (!CollectionUtils.isEmpty(incomingIdentifiers)) {
      for (ApiInventoryDOI apiIdentifier : incomingIdentifiers) {
        if (apiIdentifier.isAssignIdentifierRequest()) {
          addRecordIdentifierForAssignApiIdentifier(apiIdentifier, parentInvRec);
          changed = true;
        }
        if (apiIdentifier.isAssignIdentifierRequest()) {
          if (apiIdentifier.getId() == null) {
            throw new IllegalArgumentException(
                "'id' property not provided " + "for DOI with 'assignIdentifierRequest' flag");
          }
          Optional<DigitalObjectIdentifier> dbIdentifier =
              parentInvRec.getActiveIdentifiers().stream()
                  .filter(doi -> apiIdentifier.getId().equals(doi.getId()))
                  .findFirst();
          if (!dbIdentifier.isPresent()) {
            throw new IllegalArgumentException(
                "DOI with id: "
                    + apiIdentifier.getDoi()
                    + " doesn't match id of any pre-existing identifiers");
          }
          dbIdentifier.get().setTitle(parentInvRec.getName());
          parentInvRec.addIdentifier(dbIdentifier.get());
          changed = true;
        }
      }
    }
    if (changed) {
      parentInvRec.refreshActiveIdentifiers();
    }
    return changed;
  }
}
