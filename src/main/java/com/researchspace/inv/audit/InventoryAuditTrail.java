package com.researchspace.inv.audit;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.events.AccessEvent;
import com.researchspace.model.events.CreationEvent;
import com.researchspace.model.events.DeleteEvent;
import com.researchspace.model.events.EditingEvent;
import com.researchspace.model.events.MoveEvent;
import com.researchspace.model.events.RestoreEvent;
import com.researchspace.model.events.TransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Set of methods to listen to auditable events and log them to audit trail */
@Component
public class InventoryAuditTrail {

  private @Autowired AuditTrailService auditer;

  @TransactionalEventListener
  public void inventoryRecordCreated(CreationEvent<InventoryRecord> invRecCreated) {
    InventoryRecord invRec = invRecCreated.getCreatedItem();
    auditer.notify(new GenericEvent(invRecCreated.getCreatedBy(), invRec, AuditAction.CREATE));
  }

  @TransactionalEventListener
  public void inventoryRecordAccessed(AccessEvent<InventoryRecord> invRecAccessed) {
    InventoryRecord invRec = invRecAccessed.getAccessedItem();
    auditer.notify(new GenericEvent(invRecAccessed.getAccessedBy(), invRec, AuditAction.READ));
  }

  @TransactionalEventListener
  public void inventoryRecordDeleted(DeleteEvent<InventoryRecord> invRecDeleted) {
    InventoryRecord invRec = invRecDeleted.getDeletedItem();
    auditer.notify(new GenericEvent(invRecDeleted.getDeletedBy(), invRec, AuditAction.DELETE));
  }

  @TransactionalEventListener
  public void inventoryRecordRestored(RestoreEvent<InventoryRecord> invRecDeleted) {
    InventoryRecord invRec = invRecDeleted.getRestored();
    auditer.notify(new GenericEvent(invRecDeleted.getSubject(), invRec, AuditAction.RESTORE));
  }

  @TransactionalEventListener
  public void inventoryRecordEdited(EditingEvent<InventoryRecord> invRecEdited) {
    InventoryRecord invRec = invRecEdited.getEditedItem();
    auditer.notify(new GenericEvent(invRecEdited.getEditedBy(), invRec, AuditAction.WRITE));
  }

  @TransactionalEventListener
  public void inventoryRecordMoved(MoveEvent<InventoryRecord, Container, Container> invRecMoved) {
    InventoryRecord invRec = invRecMoved.getMovedItem();
    Container source = invRecMoved.getSourceItem();
    Container target = invRecMoved.getTargetItem();

    String description = "";
    if (source != null) {
      description += "from: " + getContainerDescriptionForMoveEventDescription(source);
    }
    if (target != null) {
      if (!description.isEmpty()) {
        description += " ";
      }
      description += "to: " + getContainerDescriptionForMoveEventDescription(target);
    }
    auditer.notify(
        new GenericEvent(invRecMoved.getMovedBy(), invRec, AuditAction.MOVE, description));
  }

  public String getContainerDescriptionForMoveEventDescription(Container container) {
    String containerName =
        container.isWorkbench()
            ? "Workbench of " + container.getOwner().getFullName()
            : container.getName();
    return containerName + " (" + container.getGlobalIdentifier() + ")";
  }

  @TransactionalEventListener
  public void inventoryRecordTransferred(TransferEvent<InventoryRecord> transferEvent) {
    InventoryRecord invRec = transferEvent.getTransferredItem();
    User transferringUser = transferEvent.getTransferringUser();
    User originalOwner = transferEvent.getOriginalOwner();
    User newOwner = transferEvent.getNewOwner();

    String description =
        String.format(
            "from user: %s to user: %s", originalOwner.getUsername(), newOwner.getUsername());
    auditer.notify(new GenericEvent(transferringUser, invRec, AuditAction.TRANSFER, description));
  }
}
