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
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Set of methods to listen to auditable events and log them to audit trail */
@Component
public class ListOfMaterialsAuditTrail {

  private @Autowired AuditTrailService auditer;
  private @Autowired MessageSourceUtils messages;

  @TransactionalEventListener
  public void listOfMaterialsCreated(ListOfMaterialsCreationEvent lomCreateEvent) {
    ListOfMaterials createdLom = lomCreateEvent.getCreatedItem();
    String description =
        messages.getMessage(
            "inventory.audit.listOfMaterials.added",
            new Object[] {createdLom.getOid().getIdString()});
    String addedItems = generatedDescriptionForUsedMaterials(createdLom.getMaterials());
    description += generateDescriptionForAddedDeletedItems(addedItems, null);
    generateDocumentWriteEventWithLomDetails(
        lomCreateEvent.getCreatedBy(), lomCreateEvent.getElnDocument(), description);
  }

  private String generatedDescriptionForUsedMaterials(List<MaterialUsage> usedMaterials) {
    return ListFormatUtils.formatList(
        usedMaterials.stream()
            .map(mu -> mu.getInventoryRecord().getGlobalIdentifier())
            .filter(s -> s != null)
            .toList());
  }

  private String generateDescriptionForAddedDeletedItems(String addedItems, String removedItems) {
    StringBuilder description = new StringBuilder();
    if (StringUtils.isNotBlank(addedItems)) {
      description
          .append(" ")
          .append(
              messages.getMessage(
                  "inventory.audit.listOfMaterials.itemsAdded", new Object[] {addedItems}));
    }
    if (StringUtils.isNotBlank(removedItems)) {
      description
          .append(" ")
          .append(
              messages.getMessage(
                  "inventory.audit.listOfMaterials.itemsRemoved", new Object[] {removedItems}));
    }
    return description.toString();
  }

  @TransactionalEventListener
  public void listOfMaterialsEdited(ListOfMaterialsEditingEvent lomEditEvent) {
    ListOfMaterials updatedLom = lomEditEvent.getEditedItem();
    List<MaterialUsage> originalMaterials = lomEditEvent.getOriginalMaterials();
    List<MaterialUsage> newMaterials = updatedLom.getMaterials();

    List<MaterialUsage> addedMaterials =
        newMaterials.stream().filter(mu -> !originalMaterials.contains(mu)).toList();
    List<MaterialUsage> removedMaterials =
        originalMaterials.stream().filter(mu -> !newMaterials.contains(mu)).toList();

    String description =
        messages.getMessage(
            "inventory.audit.listOfMaterials.edited",
            new Object[] {updatedLom.getOid().getIdString()});
    String addedItems = generatedDescriptionForUsedMaterials(addedMaterials);
    String removedItems = generatedDescriptionForUsedMaterials(removedMaterials);
    description += generateDescriptionForAddedDeletedItems(addedItems, removedItems);
    generateDocumentWriteEventWithLomDetails(
        lomEditEvent.getEditedBy(), lomEditEvent.getElnDocument(), description);
  }

  @TransactionalEventListener
  public void listOfMaterialsDeleted(ListOfMaterialsDeleteEvent lomDeleteEvent) {
    ListOfMaterials deletedLom = lomDeleteEvent.getDeletedItem();
    String description =
        messages.getMessage(
            "inventory.audit.listOfMaterials.deleted",
            new Object[] {deletedLom.getOid().getIdString()});
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
