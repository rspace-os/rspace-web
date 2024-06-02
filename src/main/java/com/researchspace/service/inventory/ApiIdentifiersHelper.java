package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryDOI;
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

  private void addRecordIdentifierForRegisteredApiIdentifier(
      ApiInventoryDOI apiIdentifier, InventoryRecord parentInvRec) {
    DigitalObjectIdentifier newDoi = new DigitalObjectIdentifier(null, null);
    newDoi.addOtherData(
        DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL,
        properties.getServerUrl() + "/public/inventory/" + newDoi.getPublicLink());

    apiIdentifier.applyChangesToDatabaseDOI(newDoi);
    parentInvRec.addIdentifier(newDoi);
  }
}
