package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.dao.ListOfMaterialsDao;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.events.ListOfMaterialsCreationEvent;
import com.researchspace.model.events.ListOfMaterialsDeleteEvent;
import com.researchspace.model.events.ListOfMaterialsEditingEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.DeltaType;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.inventory.InventoryMaterialUsageHelper;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.ListOfMaterialsApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service("listOfMaterialsApiManager")
public class ListOfMaterialsApiManagerImpl implements ListOfMaterialsApiManager {

  private @Autowired ListOfMaterialsDao lomDao;

  private @Autowired RecordManager recordManager;
  private @Autowired FieldManager fieldManager;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired InventoryMaterialUsageHelper invRecHandler;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired MessageSourceUtils messages;
  private @Autowired AuditManager auditManager;
  private @Autowired ApplicationEventPublisher publisher;

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForDocId(Long docId, User user) {
    List<Long> fieldIds = fieldManager.getFieldIdsForRecord(docId);
    List<ListOfMaterials> fieldLoms = findLomsByFieldIds(fieldIds.toArray(new Long[0]));
    return convertToOutgoingListOfApiLoms(fieldLoms);
  }

  private List<ListOfMaterials> findLomsByFieldIds(Long... elnFieldIds) {
    return lomDao.findLomsByElnFieldIds(elnFieldIds);
  }

  private List<ApiListOfMaterials> convertToOutgoingListOfApiLoms(List<ListOfMaterials> loms) {
    return loms.stream().map(ApiListOfMaterials::new).collect(Collectors.toList());
  }

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForFieldId(Long fieldId, User user) {
    List<ListOfMaterials> fieldLoms = findLomsByFieldIds(fieldId);
    return convertToOutgoingListOfApiLoms(fieldLoms);
  }

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForInvRecGlobalId(
      GlobalIdentifier globalId, User user) {
    List<ListOfMaterials> itemLoms = lomDao.findLomsByInvRecGlobalId(globalId);
    return itemLoms.stream()
        .map(lom -> createApiLomWithDocumentInfo(lom, user))
        .collect(Collectors.toList());
  }

  private ApiListOfMaterials createApiLomWithDocumentInfo(ListOfMaterials lom, User user) {
    ApiListOfMaterials apiLom = new ApiListOfMaterials(lom);
    apiLom.setElnDocument(new ApiDocumentInfo(lom.getElnField().getStructuredDocument(), user));
    return apiLom;
  }

  @Override
  public ApiListOfMaterials getListOfMaterialsById(Long lomId, User user) {
    ListOfMaterials dbLom = getIfExists(lomId);
    return createPopulatedApiLomWithDocumentInfo(dbLom, user);
  }

  private ApiListOfMaterials createPopulatedApiLomWithDocumentInfo(
      ListOfMaterials dbLom, User user) {
    ApiListOfMaterials apiLom = createApiLomWithDocumentInfo(dbLom, user);
    for (int i = 0; i < dbLom.getMaterials().size(); i++) {
      InventoryRecord invRec = dbLom.getMaterials().get(i).getInventoryRecord();
      ApiInventoryRecordInfo apiInvRec = apiLom.getMaterials().get(i).getRecord();
      sampleApiManager.setOtherFieldsForOutgoingApiInventoryRecord(apiInvRec, invRec, user);
    }
    return apiLom;
  }

  @Override
  public ListOfMaterials getIfExists(Long lomId) {
    Optional<ListOfMaterials> dbLomOpt = lomDao.getSafeNull(lomId);
    if (!dbLomOpt.isPresent()) {
      throwLomNotFoundException(lomId);
    }
    return dbLomOpt.get();
  }

  @Override
  public void assertUserCanAccessApiLom(
      ApiListOfMaterials apiLom, User user, PermissionType permission) {
    boolean canAccess = canUserAccessApiLom(apiLom.getElnFieldId(), user, permission);
    if (!canAccess) {
      throwLomNotFoundException(apiLom.getId());
    }
  }

  @Override
  public boolean canUserAccessApiLom(
      Long connectedElnFieldId, User user, PermissionType permission) {
    Field field = fieldManager.get(connectedElnFieldId, user).get();
    return permissionUtils.isPermitted(field.getStructuredDocument(), permission, user);
  }

  private void throwLomNotFoundException(Long lomId) {
    String msg = messages.getResourceNotFoundMessage("List of Materials", lomId);
    throw new NotFoundException(msg);
  }

  @Override
  public ApiListOfMaterials createNewListOfMaterials(ApiListOfMaterials incomingLom, User user) {
    Validate.notNull(incomingLom.getElnFieldId());
    Validate.notEmpty(incomingLom.getName());

    ListOfMaterials newLom = incomingLom.toListOfMaterials();
    if (incomingLom.getMaterials() != null) {
      for (ApiMaterialUsage amu : incomingLom.getMaterials()) {
        MaterialUsage materialUsage = amu.toMaterialUsage(newLom, invRecHandler);
        invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(
            materialUsage.getInventoryRecord(), user);

        newLom.getMaterials().add(materialUsage);
        if (amu.isUpdateInventoryQuantity()) {
          invRecHandler.updateSubSampleQuantityAfterUsage(
              amu.getRecord(), null, materialUsage.getUsedQuantity(), user);
        }
      }
    }
    Field elnField = fieldManager.get(incomingLom.getElnFieldId(), user).get();
    elnField.addListOfMaterials(newLom);
    ListOfMaterials savedLom = lomDao.save(newLom);
    fieldManager.save(elnField, user);

    publishNewDocRevisionAfterLomChange(elnField, savedLom, user);
    publisher.publishEvent(
        new ListOfMaterialsCreationEvent(savedLom, user, elnField.getStructuredDocument()));

    return createPopulatedApiLomWithDocumentInfo(savedLom, user);
  }

  @Override
  public ApiListOfMaterials updateListOfMaterials(ApiListOfMaterials lomUpdate, User user) {
    ListOfMaterials storedLom = getIfExists(lomUpdate.getId());
    List<MaterialUsage> originalMaterials = new ArrayList<>(storedLom.getMaterials());
    boolean lomChanged =
        lomUpdate.applyChangesToDatabaseListOfMaterials(storedLom, invRecHandler, user);
    if (lomChanged) {
      for (MaterialUsage mu : storedLom.getMaterials()) {
        invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(mu.getInventoryRecord(), user);
      }
      publishNewDocRevisionAfterLomChange(storedLom.getElnField(), storedLom, user);
      publisher.publishEvent(
          new ListOfMaterialsEditingEvent(
              storedLom, user, storedLom.getElnField().getStructuredDocument(), originalMaterials));
    }

    return getListOfMaterialsById(lomUpdate.getId(), user);
  }

  @Override
  public void deleteListOfMaterials(Long lomId, User user) {
    ListOfMaterials lom = getIfExists(lomId);
    if (lom == null) {
      return;
    }
    Field elnField = lom.getElnField();
    elnField.removeListOfMaterials(lom);
    fieldManager.save(elnField, user);
    lomDao.remove(lom.getId());

    publishNewDocRevisionAfterLomChange(elnField, lom, user);
    publisher.publishEvent(
        new ListOfMaterialsDeleteEvent(lom, user, elnField.getStructuredDocument()));
  }

  private void publishNewDocRevisionAfterLomChange(Field elnField, ListOfMaterials lom, User user) {
    recordManager.forceVersionUpdate(
        elnField.getStructuredDocument().getId(),
        DeltaType.LIST_OF_MATERIALS_CHG,
        DeltaType.LIST_OF_MATERIALS_CHG + "-" + elnField.getName(),
        user);
  }

  @Override
  public ApiListOfMaterials getListOfMaterialsRevision(Long lomId, Long revisionId) {
    AuditedEntity<ListOfMaterials> entityRevision =
        auditManager.getObjectForRevision(ListOfMaterials.class, lomId, revisionId);
    if (entityRevision == null) {
      return null;
    }
    ListOfMaterials invRec = entityRevision.getEntity();
    return new ApiListOfMaterials(invRec);
  }

  @Override
  public void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }
}
