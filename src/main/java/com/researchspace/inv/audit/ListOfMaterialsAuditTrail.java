package com.researchspace.inv.audit;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.events.ListOfMaterialsCreationEvent;
import com.researchspace.model.events.ListOfMaterialsDeleteEvent;
import com.researchspace.model.events.ListOfMaterialsEditingEvent;
import com.researchspace.model.record.StructuredDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Set of methods to listen to auditable events and log them to audit trail */
@Component
public class ListOfMaterialsAuditTrail {

  private @Autowired AuditTrailService auditer;

  @TransactionalEventListener
  public void listOfMaterialsCreated(ListOfMaterialsCreationEvent lomCreateEvent) {
    ListOfMaterials createdLom = lomCreateEvent.getCreatedItem();
    String description = "Added List of Materials " + createdLom.getOid().getIdString() + ".";
    String addedItems = generatedDescriptionForUsedMaterials(createdLom.getMaterials());
    description += generateDescriptionForAddedDeletedItems(addedItems, null);
    generateDocumentWriteEventWithLomDetails(
        lomCreateEvent.getCreatedBy(), lomCreateEvent.getElnDocument(), description);
  }

  private String generatedDescriptionForUsedMaterials(List<MaterialUsage> usedMaterials) {
    String commaSeparatedGlobalIds =
        usedMaterials.stream()
            .map(mu -> mu.getInventoryRecord().getGlobalIdentifier())
            .filter(s -> s != null)
            .collect(Collectors.joining(", "));
    return commaSeparatedGlobalIds;
  }

  private String generateDescriptionForAddedDeletedItems(String addedItems, String removedItems) {
    List<String> descriptionsToJoin = new ArrayList<>();
    if (StringUtils.isNotBlank(addedItems)) {
      descriptionsToJoin.add(" Added inventory items: " + addedItems + ".");
    }
    if (StringUtils.isNotBlank(removedItems)) {
      descriptionsToJoin.add(" Removed inventory items: " + removedItems + ".");
    }
    return descriptionsToJoin.stream().collect(Collectors.joining());
  }

  @TransactionalEventListener
  public void listOfMaterialsEdited(ListOfMaterialsEditingEvent lomEditEvent) {
    ListOfMaterials updatedLom = lomEditEvent.getEditedItem();
    List<MaterialUsage> originalMaterials = lomEditEvent.getOriginalMaterials();
    List<MaterialUsage> newMaterials = updatedLom.getMaterials();

    List<MaterialUsage> addedMaterials =
        newMaterials.stream()
            .filter(mu -> !originalMaterials.contains(mu))
            .collect(Collectors.toList());
    List<MaterialUsage> removedMaterials =
        originalMaterials.stream()
            .filter(mu -> !newMaterials.contains(mu))
            .collect(Collectors.toList());

    String description = "Edited List of Materials " + updatedLom.getOid().getIdString() + ".";
    String addedItems = generatedDescriptionForUsedMaterials(addedMaterials);
    String removedItems = generatedDescriptionForUsedMaterials(removedMaterials);
    description += generateDescriptionForAddedDeletedItems(addedItems, removedItems);
    generateDocumentWriteEventWithLomDetails(
        lomEditEvent.getEditedBy(), lomEditEvent.getElnDocument(), description);
  }

  @TransactionalEventListener
  public void listOfMaterialsDeleted(ListOfMaterialsDeleteEvent lomDeleteEvent) {
    ListOfMaterials deletedLom = lomDeleteEvent.getDeletedItem();
    String description = "Deleted List of Materials " + deletedLom.getOid().getIdString() + ".";
    String removedItems = generatedDescriptionForUsedMaterials(deletedLom.getMaterials());
    description += generateDescriptionForAddedDeletedItems(null, removedItems);
    generateDocumentWriteEventWithLomDetails(
        lomDeleteEvent.getDeletedBy(), lomDeleteEvent.getElnDocument(), description);
  }

  private void generateDocumentWriteEventWithLomDetails(
      User user, StructuredDocument elnDocument, String description) {
    auditer.notify(new GenericEvent(user, elnDocument, AuditAction.WRITE, description));
  }
}
