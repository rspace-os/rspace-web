package com.researchspace.service.inventory;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.MovableInventoryRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** To deal with moving inventory items. */
@Component
public class InventoryMoveHelper {

  @Autowired private InventoryRecordRetriever invRecRetriever;
  @Autowired protected InventoryPermissionUtils invPermissions;

  protected @Autowired ApplicationEventPublisher publisher;

  /**
   * Moves inventory record according to provided target parent and location.
   *
   * <p>Will return false if the move request wasn't actioned (e.g. the record was already stored in
   * the target location). Will throw an exception if the move request is illegal.
   *
   * @param dbRecord record to move
   * @param targetParent target container of move action. May be null if target location is provided
   *     and is enough (e.g. move into image container)
   * @param targetLocation target container location of move action. May be null if target parent is
   *     provided and is enough (e.g. move into list container)
   * @param user user requesting the move operation
   * @return true if move action was executed
   * @throws IllegalArgumentException when move is illegal e.g. target container doesn't accept the
   *     type of record that is moved, or when provided coordinates are incorrect, etc.
   */
  public boolean moveRecordToTargetParentAndLocation(
      MovableInventoryRecord dbRecord,
      ApiContainerInfo targetParent,
      ApiContainerLocation targetLocation,
      User user) {

    Container targetParentContainer =
        isValidMoveRequest(dbRecord, targetParent, targetLocation, user);
    if (targetParentContainer == null) {
      return false;
    }

    /* for list container no need to use target location */
    if (targetParentContainer.isListLayoutContainer() || targetParentContainer.isWorkbench()) {
      dbRecord.moveToNewParent(targetParentContainer);
      return true;
    }

    /* for others container types (grid/image) target location is necessary */
    Optional<ContainerLocation> targetDbLocationOpt =
        targetParentContainer.findSavedLocationByIdOrCoordinates(
            targetLocation.getId(), targetLocation.getCoordX(), targetLocation.getCoordY());
    if (targetDbLocationOpt.isPresent()) {
      dbRecord.moveToNewParentAndLocation(targetParentContainer, targetDbLocationOpt.get());
    } else {
      dbRecord.moveToNewParentWithCoords(
          targetParentContainer, targetLocation.getCoordX(), targetLocation.getCoordY());
    }
    return true;
  }

  /**
   * Checks whether move action should be executed for given record and target parent/location
   * provided in incoming record info.
   *
   * <p>Will return target container to move into, or null if the move shouldn't be actioned (e.g.
   * the record is already stored in the target location.
   *
   * <p>Will throw an exception if the move request is illegal (e.g. trying move to image container
   * without providing target location, or no permission to move into target container).
   *
   * @return target container if move request is valid and should proceed
   */
  private Container isValidMoveRequest(
      MovableInventoryRecord dbRecord,
      ApiContainerInfo targetParent,
      ApiContainerLocation targetLocation,
      User user) {

    if (targetParent == null && targetLocation == null) {
      return null;
    }

    invPermissions.assertUserCanEditInventoryRecord(dbRecord, user);
    Container targetContainer =
        getTargetContainerFromTargetParentAndLocation(targetParent, targetLocation, user);

    // ensure target container can store current record
    targetContainer.assertCanStoreRecord(dbRecord);

    // list container can always store more records, and only target parent is required
    if (targetContainer.isListLayoutContainer() || targetContainer.isWorkbench()) {
      if (targetContainer.getId().equals(dbRecord.getParentId())) {
        return null; // moving to the same list container - ignore request
      }
      return targetContainer;
    }

    // for grid/image container target location is necessary
    if (targetLocation == null) {
      throw new ApiRuntimeException("move.failure.no.target.location.for.grid.image.container");
    }

    Optional<ContainerLocation> targetDbLocationOpt =
        targetContainer.findSavedLocationByIdOrCoordinates(
            targetLocation.getId(), targetLocation.getCoordX(), targetLocation.getCoordY());
    if (targetDbLocationOpt.isPresent()) {
      ContainerLocation targetDbLocation = targetDbLocationOpt.get();
      Long currentLocationId =
          dbRecord.getParentLocation() == null ? null : dbRecord.getParentLocation().getId();
      if (targetDbLocation.getId().equals(currentLocationId)) {
        return null; // moving to the same location
      }
    } else if (targetContainer.isImageLayoutContainer()) {
      throw new ApiRuntimeException("move.failure.target.image.container.location.not.found");
    }
    return targetContainer;
  }

  private Container getTargetContainerFromTargetParentAndLocation(
      ApiContainerInfo targetParentInfo, ApiContainerLocation targetLocationInfo, User user) {

    Container targetContainer = null;
    if (targetParentInfo != null && targetParentInfo.getId() != null) {
      targetContainer = invRecRetriever.getContainerIfExists(targetParentInfo.getId());
    } else if (targetLocationInfo != null && targetLocationInfo.getId() != null) {
      targetContainer = invRecRetriever.getContainerByLocationId(targetLocationInfo.getId());
    }
    boolean canMoveIntoTargetContainer =
        targetContainer == null
            ? false
            : invPermissions.canUserEditInventoryRecord(targetContainer, user);
    if (targetContainer == null || !canMoveIntoTargetContainer) {
      throw new ApiRuntimeException("move.failure.cannot.locate.target.container");
    }
    return targetContainer;
  }

  public boolean removeRecordFromCurrentParent(MovableInventoryRecord dbRecord) {
    if (dbRecord.getParentContainer() != null) {
      dbRecord.removeFromCurrentParent();
      dbRecord.setLastMoveDate(Instant.now());
      return true;
    }
    return false;
  }

  public List<ApiInventoryRecordInfo> runBulkRecordMove(
      List<ApiInventoryRecordInfo> incomingRecords, User user) {
    List<ApiInventoryRecordInfo> result = new ArrayList<>();
    if (incomingRecords != null) {
      List<InventoryRecord> dbRecords = new ArrayList<>();
      // retrieve records
      for (ApiInventoryRecordInfo recInfo : incomingRecords) {
        ApiInventoryRecordType recInfoType = recInfo.getType();
        switch (recInfoType) {
          case CONTAINER:
            dbRecords.add(invRecRetriever.getContainerIfExists(recInfo.getId()));
            break;
          case SUBSAMPLE:
            dbRecords.add(invRecRetriever.getSubSampleIfExists(recInfo.getId()));
            break;
          default:
            throw new IllegalArgumentException(
                "bulk move doesn't support records of type: " + recInfo.getClass().getName());
        }
      }

      // find all records that are going to change location and initially move them to workbench
      Container workbench = invRecRetriever.getWorkbenchForUser(user);
      List<ContainerLocation> dbRecordsOrgLocation = new ArrayList<>();

      for (int i = 0; i < incomingRecords.size(); i++) {
        MovableInventoryRecord dbRecord = (MovableInventoryRecord) dbRecords.get(i);
        ApiInventoryRecordInfo recInfo = incomingRecords.get(i);
        dbRecordsOrgLocation.add(dbRecord.getParentLocation());
        Container targetParent =
            isValidMoveRequest(
                dbRecord, recInfo.getParentContainer(), recInfo.getParentLocation(), user);
        if (targetParent != null) {
          dbRecord.moveToNewParent(workbench);
        }
      }
      // move each record to new parent and location
      for (int i = 0; i < incomingRecords.size(); i++) {
        MovableInventoryRecord dbRecord = (MovableInventoryRecord) dbRecords.get(i);
        ApiInventoryRecordInfo recInfo = incomingRecords.get(i);

        if (recInfo.isRemoveFromParentContainerRequest()) {
          removeRecordFromCurrentParent(dbRecord);
        } else if (recInfo.getParentContainer() != null || recInfo.getParentLocation() != null) {
          moveRecordToTargetParentAndLocation(
              dbRecord, recInfo.getParentContainer(), recInfo.getParentLocation(), user);
        }

        // add audit event if item has a new location
        ContainerLocation orgLocation = dbRecordsOrgLocation.get(i);
        if (dbRecord.getParentLocation() != orgLocation) {
          Container orgParentContainer = orgLocation != null ? orgLocation.getContainer() : null;
          publisher.publishEvent(
              new InventoryMoveEvent(
                  dbRecord, orgParentContainer, dbRecord.getParentContainer(), user));
        }

        result.add(ApiInventoryRecordInfo.fromInventoryRecord(dbRecord));
      }
    }
    return result;
  }

  /*
   * ================
   *  for testing
   * ================
   */

  public void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }
}
