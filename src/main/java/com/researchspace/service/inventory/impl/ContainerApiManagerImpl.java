package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryMoveHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("containerApiManager")
public class ContainerApiManagerImpl extends InventoryApiManagerImpl
    implements ContainerApiManager {

  public static final String CONTAINER_DEFAULT_NAME = "Generic Container";

  @Autowired private InventoryMoveHelper moveHelper;

  @Override
  public ISearchResults<ApiContainerInfo> getTopContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {
    ISearchResults<Container> dbSearchResult =
        containerDao.getTopContainersForUser(pgCrit, ownedBy, deletedOption, null, user);
    return getOutgoingContainerSearchResults(pgCrit, user, dbSearchResult);
  }

  private ISearchResults<ApiContainerInfo> getOutgoingContainerSearchResults(
      PaginationCriteria<Container> pgCrit, User user, ISearchResults<Container> dbSearchResult) {

    List<ApiContainerInfo> containerInfos = new ArrayList<>();
    for (Container container : dbSearchResult.getResults()) {
      ApiContainerInfo apiContainerInfo = new ApiContainerInfo(container);
      setOtherFieldsForOutgoingApiInventoryRecord(apiContainerInfo, container, user);
      containerInfos.add(apiContainerInfo);
    }

    return new SearchResultsImpl<>(containerInfos, pgCrit, dbSearchResult.getTotalHits());
  }

  @Override
  public boolean exists(long id) {
    return containerDao.exists(id);
  }

  @Override
  public Container assertUserCanReadContainer(Long id, User user) {
    Container container = getIfExists(id);
    invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(container, user);
    return container;
  }

  @Override
  public Container assertUserCanEditContainer(Long id, User user) {
    Container container = getIfExists(id);
    invPermissions.assertUserCanEditInventoryRecord(container, user);
    assertWorkbenchContainer(container, false); // workbench cannot be edited
    return container;
  }

  @Override
  public Container assertUserCanDeleteContainer(Long id, User user) {
    Container container = getIfExists(id);
    invPermissions.assertUserCanDeleteInventoryRecord(container, user);
    assertWorkbenchContainer(container, false); // workbench cannot be edited
    return container;
  }

  @Override
  public Container assertUserCanTransferContainer(Long id, User user) {
    Container container = getIfExists(id);
    invPermissions.assertUserCanTransferInventoryRecord(container, user);
    assertWorkbenchContainer(container, false); // workbench cannot be transferred
    return container;
  }

  public void assertWorkbenchContainer(Container container, boolean isWorkbench) {
    if (isWorkbench && !container.isWorkbench()) {
      throw new IllegalArgumentException(
          "Container with id " + container.getId() + " is not a workbench");
    }
    if (!isWorkbench && container.isWorkbench()) {
      throw new IllegalArgumentException(
          "Container with id " + container.getId() + " is a workbench");
    }
  }

  @Override
  public ApiContainer getApiContainerById(Long id, User user) {
    return getApiContainerById(id, true, user);
  }

  @Override
  public ApiContainer getApiContainerById(Long id, boolean includeContent, User user) {
    return doGetContainer(id, includeContent, user, false);
  }

  @Override
  public ApiContainer getApiWorkbenchById(Long id, User user) {
    return getApiWorkbenchById(id, true, user);
  }

  @Override
  public ApiContainer getApiWorkbenchById(Long id, boolean includeContent, User user) {
    return doGetContainer(id, includeContent, user, true);
  }

  private ApiContainer doGetContainer(
      Long id, boolean includeContent, User user, boolean isWorkbench) {
    Container container = getIfExists(id);
    assertWorkbenchContainer(container, isWorkbench);
    if (!isWorkbenchOfCurrentUser(container, user)) {
      publisher.publishEvent(new InventoryAccessEvent(container, user));
    }
    return getPopulatedApiContainer(container, includeContent, user);
  }

  private boolean isWorkbenchOfCurrentUser(Container container, User currentUser) {
    return container.isWorkbench()
        && container.getOwner().getUsername().equals(currentUser.getUsername());
  }

  private ApiContainer getPopulatedApiContainer(
      Container container, boolean includeContent, User user) {
    ApiContainer result = new ApiContainer(container, includeContent);
    setOtherFieldsForOutgoingApiInventoryRecord(result, container, user);
    populateSharingPermissions(result.getSharedWith(), container);

    // populate some fields in content
    if (result.getLocations() != null) {
      for (ApiContainerLocationWithContent location : result.getLocations()) {
        ApiInventoryRecordInfo apiContentRec = location.getContent();
        if (apiContentRec != null) {
          setOtherFieldsForOutgoingApiInventoryRecord(apiContentRec, location.getDbContent(), user);
        }
      }
    }
    return result;
  }

  @Override
  public ApiContainer getApiContainerIfExists(Long id, User user) {
    Container container = getIfExists(id);
    return new ApiContainer(container);
  }

  @Override
  public Container getContainerById(Long id, User user) {
    return getIfExists(id);
  }

  @Override
  public ApiInventorySearchResult searchForContentOfContainer(
      Long containerId,
      String ownedBy,
      InventorySearchType searchType,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    Container container = getIfExists(containerId);
    boolean canRead = invPermissions.canUserReadInventoryRecord(container, user);

    if (!canRead
        || searchType == InventorySearchType.SAMPLE
        || searchType == InventorySearchType.TEMPLATE) {
      return ApiInventorySearchResult.emptyResult();
    }

    List<InventoryRecord> children = new ArrayList<InventoryRecord>();
    for (ContainerLocation location : container.getLocations()) {
      if (location.getStoredRecord() != null) {
        children.add(location.getStoredRecord());
      }
    }
    if (searchType != null && searchType != InventorySearchType.ALL) {
      children =
          children.stream()
              .filter(ir -> ir.getType().equals(InventoryRecordType.valueOf(searchType.toString())))
              .collect(Collectors.toList());
    }
    if (StringUtils.isNotBlank(ownedBy)) {
      children =
          children.stream()
              .filter(ir -> ir.getOwner().getUsername().equals(ownedBy))
              .collect(Collectors.toList());
    }
    return sortRepaginateConvertToApiInventorySearchResult(pgCrit, children, user);
  }

  @Override
  public ApiContainer createNewApiContainer(ApiContainer apiContainer, User user) {

    String name = getNameForIncomingApiContainer(apiContainer);
    Container newContainer = recordFactory.createListContainer(name, user);
    setBasicFieldsFromNewIncomingApiInventoryRecord(newContainer, apiContainer, user);
    if (!apiContainer.isRemoveFromParentContainerRequest()) {
      setWorkbenchAsParentForNewContainer(newContainer, user);
    }
    moveHelper.moveRecordToTargetParentAndLocation(
        newContainer, apiContainer.getParentContainer(), apiContainer.getParentLocation(), user);
    setImageContainerFields(newContainer, apiContainer);
    apiContainer.applyChangesToDatabaseGridLayout(newContainer);
    apiContainer.applyContainerContentFlagsToDatabaseContainer(newContainer);

    Container savedContainer = containerDao.save(newContainer);
    saveIncomingContainerImages(savedContainer, apiContainer, user);
    publishAuditEventsForCreatedContainer(user, savedContainer);

    return getPopulatedApiContainer(getContainerById(savedContainer.getId(), user), true, user);
  }

  private void setWorkbenchAsParentForNewContainer(Container container, User user) {
    Container workbench = containerDao.getWorkbenchForUser(user);
    if (container.getParentContainer() == null) {
      container.moveToNewParent(workbench);
      container.setLastMoveDate(null); // for containers created in workspace don't start move timer
    }
  }

  private void publishAuditEventsForCreatedContainer(User user, Container container) {
    publisher.publishEvent(new InventoryCreationEvent(container, user));
    Container containerParent = container.getParentContainer();
    if (containerParent != null
        && !ContainerType.WORKBENCH.equals(containerParent.getContainerType())) {
      publisher.publishEvent(new InventoryMoveEvent(container, null, containerParent, user));
    }
  }

  private String getNameForIncomingApiContainer(ApiContainerInfo prototypeSample) {
    return StringUtils.isNotBlank(prototypeSample.getName())
        ? prototypeSample.getName()
        : CONTAINER_DEFAULT_NAME;
  }

  private void setImageContainerFields(Container newContainer, ApiContainer apiContainer) {
    if ("IMAGE".equals(apiContainer.getCType())) {
      newContainer.setContainerType(ContainerType.IMAGE);
    }
    if (apiContainer.getLocations() != null) {
      for (ApiContainerLocation apiContainerLocation : apiContainer.getLocations()) {
        newContainer.createNewImageContainerLocation(
            apiContainerLocation.getCoordX(), apiContainerLocation.getCoordY());
      }
    }
  }

  @Override
  public ApiContainer updateApiContainer(ApiContainer apiContainer, User user) {
    Container dbContainer = getIfExists(apiContainer.getId());
    ApiContainer original = new ApiContainer(dbContainer);
    boolean temporaryLock = lockItemForEdit(dbContainer, user);

    try {
      dbContainer = getIfExists(dbContainer.getId());
      Container orgParentContainer = dbContainer.getParentContainer();
      boolean contentChanged =
          extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseContainer(
              apiContainer, dbContainer, user);
      contentChanged |=
          barcodesHelper.createDeleteRequestedBarcodes(
              apiContainer.getBarcodes(), dbContainer, user);
      contentChanged |=
          identifiersHelper.createDeleteRequestedIdentifiers(
              apiContainer.getIdentifiers(), dbContainer, user);
      contentChanged |= apiContainer.applyChangesToDatabaseContainer(dbContainer, user);
      contentChanged |= saveSharingACLForIncomingApiInvRec(dbContainer, apiContainer);
      contentChanged |= saveIncomingContainerImages(dbContainer, apiContainer, user);
      boolean moveSuccessful = false;
      if (apiContainer.isRemoveFromParentContainerRequest()) {
        moveSuccessful = moveHelper.removeRecordFromCurrentParent(dbContainer);
      } else {
        moveSuccessful =
            moveHelper.moveRecordToTargetParentAndLocation(
                dbContainer,
                apiContainer.getParentContainer(),
                apiContainer.getParentLocation(),
                user);
      }

      if (contentChanged) {
        dbContainer.setModificationDate(new Date());
        dbContainer.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
        publisher.publishEvent(new InventoryEditingEvent(dbContainer, user));
      }
      if (moveSuccessful) {
        publisher.publishEvent(
            new InventoryMoveEvent(
                dbContainer, orgParentContainer, dbContainer.getParentContainer(), user));
      }
      if (contentChanged || moveSuccessful) {
        containerDao.save(dbContainer);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbContainer, user);
      }
    }
    ApiContainer updated = getPopulatedApiContainer(getIfExists(apiContainer.getId()), true, user);
    updateOntologyOnUpdate(original, updated, user);
    return updated;
  }

  @Override
  public ApiContainer changeApiContainerOwner(ApiContainer apiContainer, User user) {
    Validate.notNull(apiContainer.getOwner(), "'owner' field not present");
    Validate.notNull(apiContainer.getOwner().getUsername(), "'owner.username' field not present");

    assertUserCanTransferContainer(apiContainer.getId(), user);

    Container dbContainer = getIfExists(apiContainer.getId());
    boolean temporaryLock = lockItemForEdit(dbContainer, user);

    try {
      dbContainer = getIfExists(dbContainer.getId());
      User originalOwner = dbContainer.getOwner();
      String newOwnerUsername = apiContainer.getOwner().getUsername();

      if (!originalOwner.getUsername().equals(newOwnerUsername)) {
        Validate.isTrue(
            userManager.userExists(newOwnerUsername),
            "Target user [" + newOwnerUsername + "] not found");
        User newOwner = userManager.getUserByUsername(newOwnerUsername);
        dbContainer.setOwner(newOwner);
        moveItemBetweenWorkbenches(dbContainer, originalOwner, newOwner);

        publisher.publishEvent(
            new InventoryTransferEvent(dbContainer, user, originalOwner, newOwner));
        containerDao.save(dbContainer);
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbContainer, user);
      }
    }

    ApiContainer changedOwnerContainer =
        getPopulatedApiContainer(getIfExists(apiContainer.getId()), true, user);
    updateOntologyOnRecordChanges(changedOwnerContainer, user);
    return changedOwnerContainer;
  }

  /**
   * Save incoming image/locationsImage.
   *
   * @returns true if any images were saved
   */
  private boolean saveIncomingContainerImages(
      Container dbContainer, ApiContainer apiContainer, User testUser) {
    boolean result = false;
    try {
      String containerImage = apiContainer.getNewBase64Image();
      if (containerImage != null) {
        doSaveImage(
            dbContainer, containerImage, testUser, Container.class, con -> containerDao.save(con));
        result = true;
      }
      String locationsImage = apiContainer.getNewBase64LocationsImage();
      if (locationsImage != null) {
        saveContainerLocationsImage(dbContainer, locationsImage, testUser);
        result = true;
      }

    } catch (IOException ioe) {
      throw new IllegalStateException("Can't save container image", ioe);
    }
    return result;
  }

  private Container saveContainerLocationsImage(Container container, String base64Image, User user)
      throws IOException {
    FileProperty locationsImageFileProp = saveImageFile(user, base64Image, false);
    container.setLocationsImageFileProperty(locationsImageFileProp);

    container = containerDao.save(container);
    return container;
  }

  @Override
  public ApiContainer moveContainerToTopLevel(Long containerId, User user) {
    assertUserCanEditContainer(containerId, user);

    ApiContainer apiContainer = new ApiContainer();
    apiContainer.setId(containerId);
    apiContainer.setRemoveFromParentContainerRequest(true);

    return updateApiContainer(apiContainer, user);
  }

  @Override
  Container getIfExists(Long id) {
    boolean exists = containerDao.exists(id);
    if (!exists) {
      throw new NotFoundException("No container with id: " + id);
    }
    return containerDao.get(id);
  }

  @Override
  public ApiContainer markContainerAsDeleted(Long id, User user) {
    Container dbContainer = assertUserCanDeleteContainer(id, user);
    boolean temporaryLock = lockItemForEdit(dbContainer, user);
    try {
      dbContainer = getIfExists(dbContainer.getId());
      if (!dbContainer.isDeleted()) {
        if (dbContainer.getContentCount() > 0) {
          throw new ApiRuntimeException(
              "container.deletion.failure.not.empty", dbContainer.getGlobalIdentifier());
        }
        dbContainer.removeFromCurrentParent();
        dbContainer.setRecordDeleted(true);
        dbContainer = containerDao.save(dbContainer);
        publisher.publishEvent(new InventoryDeleteEvent(dbContainer, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbContainer, user);
      }
    }

    ApiContainer deleted = getPopulatedApiContainer(getIfExists(id), true, user);
    updateOntologyOnRecordChanges(deleted, user);
    return deleted;
  }

  @Override
  public ApiContainer restoreDeletedContainer(Long id, User user) {
    Container dbContainer = assertUserCanDeleteContainer(id, user);
    boolean temporaryLock = lockItemForEdit(dbContainer, user);
    try {
      dbContainer = getIfExists(dbContainer.getId());
      if (dbContainer.isDeleted()) {
        Container workbench = containerDao.getWorkbenchForUser(user);
        dbContainer.moveToNewParent(workbench);
        dbContainer.setRecordDeleted(false);
        dbContainer = containerDao.save(dbContainer);
        publisher.publishEvent(new InventoryRestoreEvent(dbContainer, user));
      }
    } finally {
      if (temporaryLock) {
        unlockItemAfterEdit(dbContainer, user);
      }
    }
    ApiContainer restored = getPopulatedApiContainer(getIfExists(id), true, user);
    updateOntologyOnRecordChanges(restored, user);
    return restored;
  }

  @Override
  public ApiContainer duplicate(Long containerId, User user) {
    Container dbContainer = assertUserCanReadContainer(containerId, user);
    assertWorkbenchContainer(dbContainer, false); // workbench cannot be duplicated

    Container workbench = containerDao.getWorkbenchForUser(user);
    Container copy = dbContainer.copy(user);
    setWorkbenchAsParentForNewInventoryRecord(workbench, copy);
    copy = containerDao.save(copy);
    publisher.publishEvent(new InventoryCreationEvent(copy, user));
    return new ApiContainer(copy);
  }

  @Override
  public ISearchResults<ApiContainerInfo> getWorkbenchesForUser(
      PaginationCriteria<Container> pgCrit, String ownedBy, User user) {
    ISearchResults<Container> dbSearchResult =
        containerDao.getTopContainersForUser(pgCrit, ownedBy, null, ContainerType.WORKBENCH, user);
    return getOutgoingContainerSearchResults(pgCrit, user, dbSearchResult);
  }

  @Override
  public Long getWorkbenchIdForUser(User user) {
    return containerDao.getWorkbenchIdForUser(user);
  }
}
