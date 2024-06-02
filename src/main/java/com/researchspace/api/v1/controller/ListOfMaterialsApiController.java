package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ListOfMaterialsApi;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.FieldManager;
import com.researchspace.service.inventory.ListOfMaterialsApiManager;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class ListOfMaterialsApiController extends BaseApiInventoryController
    implements ListOfMaterialsApi {

  @Autowired private ListOfMaterialsApiManager listOfMaterialsMgr;

  private @Autowired FieldManager fieldManager;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForDocument(
      @PathVariable Long docId, @RequestAttribute(name = "user") User user) throws BindException {

    assertUserCanReadElnDoc(docId, user);

    List<ApiListOfMaterials> results = listOfMaterialsMgr.getListOfMaterialsForDocId(docId, user);
    return results;
  }

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForField(
      @PathVariable Long elnFieldId, @RequestAttribute(name = "user") User user)
      throws BindException {

    assertUserCanAccessElnField(elnFieldId, user, PermissionType.READ);

    List<ApiListOfMaterials> results =
        listOfMaterialsMgr.getListOfMaterialsForFieldId(elnFieldId, user);
    return results;
  }

  @Override
  public List<ApiListOfMaterials> getListOfMaterialsForInventoryItem(
      @PathVariable String globalId, @RequestAttribute(name = "user") User user)
      throws BindException {

    Validate.isTrue(GlobalIdentifier.isValid(globalId), "not a valid global id: " + globalId);
    GlobalIdentifier oid = new GlobalIdentifier(globalId);
    assertUserCanReadInventoryRecord(oid, user);

    List<ApiListOfMaterials> results =
        listOfMaterialsMgr.getListOfMaterialsForInvRecGlobalId(oid, user);
    return results;
  }

  @Override
  public ApiListOfMaterials getListOfMaterialsById(
      @PathVariable Long lomId, @RequestAttribute(name = "user") User user) throws BindException {

    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(lomId, user);
    listOfMaterialsMgr.assertUserCanAccessApiLom(lom, user, PermissionType.READ);

    return lom;
  }

  @Override
  public ApiListOfMaterials addListOfMaterials(
      @RequestBody @Valid ApiListOfMaterials newLom,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    if (newLom.getElnFieldId() == null) {
      errors.addError(new ObjectError("List of materials", "elnFieldId cannot be null"));
    }
    if (StringUtils.isEmpty(newLom.getName())) {
      errors.addError(new ObjectError("List of materials", "name cannot be empty"));
    }
    throwBindExceptionIfErrors(errors);

    assertUserCanAccessElnField(newLom.getElnFieldId(), user, PermissionType.WRITE);
    return listOfMaterialsMgr.createNewListOfMaterials(newLom, user);
  }

  @Override
  public ApiListOfMaterials updateListOfMaterials(
      @PathVariable Long lomId,
      @RequestBody @Valid ApiListOfMaterials lomUpdate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(lomId, user);
    listOfMaterialsMgr.assertUserCanAccessApiLom(lom, user, PermissionType.WRITE);

    lomUpdate.setId(lomId);
    ApiListOfMaterials savedLom = listOfMaterialsMgr.updateListOfMaterials(lomUpdate, user);
    return savedLom;
  }

  @Override
  public ApiListOfMaterials deleteListOfMaterials(
      @PathVariable Long lomId, @RequestAttribute(name = "user") User user) {

    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(lomId, user);
    listOfMaterialsMgr.assertUserCanAccessApiLom(lom, user, PermissionType.WRITE);

    listOfMaterialsMgr.deleteListOfMaterials(lomId, user);
    return lom;
  }

  @Override
  public boolean canUserEditListOfMaterials(
      @PathVariable Long lomId, @RequestAttribute(name = "user") User user) {

    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(lomId, user);
    listOfMaterialsMgr.assertUserCanAccessApiLom(lom, user, PermissionType.READ);

    return listOfMaterialsMgr.canUserAccessApiLom(lom.getElnFieldId(), user, PermissionType.WRITE);
  }

  @Override
  public ApiListOfMaterials getListOfMaterialsRevision(
      @PathVariable Long lomId,
      @PathVariable Long revisionId,
      @RequestAttribute(name = "user") User user) {

    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(lomId, user);
    listOfMaterialsMgr.assertUserCanAccessApiLom(lom, user, PermissionType.READ);

    ApiListOfMaterials lomRevision =
        listOfMaterialsMgr.getListOfMaterialsRevision(lomId, revisionId);
    return lomRevision;
  }

  /*
   * permission check methods below
   */

  private void assertUserCanReadElnDoc(Long docId, User user) {
    boolean exists = recordManager.exists(docId);
    if (!exists) {
      throwNotFoundException("Document", docId);
    }
    BaseRecord record = recordManager.get(docId);
    boolean canRead =
        record.isStructuredDocument()
            && permissionUtils.isPermitted(record, PermissionType.READ, user);
    if (!canRead) {
      throwNotFoundException("Document", docId);
    }
  }

  private void assertUserCanAccessElnField(Long elnFieldId, User user, PermissionType permission) {
    Optional<Field> fieldOptional = fieldManager.get(elnFieldId, user);
    if (!fieldOptional.isPresent()) {
      throwNotFoundException("Field", elnFieldId);
    }
    boolean canAccess =
        permissionUtils.isPermitted(fieldOptional.get().getStructuredDocument(), permission, user);
    if (!canAccess) {
      throwNotFoundException("Field", elnFieldId);
    }
  }

  private void throwNotFoundException(String type, Long id) {
    throw new NotFoundException(createNotFoundMessage(type, id));
  }
}
