package com.researchspace.service.inventory;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.record.IRecordFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * To deal with API extra fields conversion when fields are sent between RSpace server and API
 * client.
 */
@Component
public class ApiExtraFieldsHelper implements Validator {

  private IRecordFactory recordFactory;
  private final InventoryLinkValidator linkValidator = new InventoryLinkValidator();

  @Autowired private InventoryLinkManager inventoryLinkManager;

  public ApiExtraFieldsHelper(@Autowired IRecordFactory recordFactory) {
    this.recordFactory = recordFactory;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiExtraField.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiExtraField aef = (ApiExtraField) target;
    // if this is a new field, id is null, so we should reject no name
    if (aef.getId() == null) {
      ValidationUtils.rejectIfEmptyOrWhitespace(
          errors,
          "name",
          "errors.required",
          new Object[] {new DefaultMessageSourceResolvable("label.nameLowercase")});
    }
    if (aef.getTypeAsFieldType() == FieldType.LINK) {
      validateLinkPayload(aef, errors);
      return;
    }
    ExtraField extraField = recordFactory.createExtraField(aef.getTypeAsFieldType());
    String validationMsg = extraField.validateNewData(aef.getContent());
    if (!StringUtils.isEmpty(validationMsg)) {
      errors.rejectValue(
          "content", "errors.inventory.field.validation", new Object[] {validationMsg}, null);
    }
  }

  private void validateLinkPayload(ApiExtraField aef, Errors errors) {
    if (aef.getLink() == null) {
      errors.rejectValue(
          "link",
          "errors.required",
          new Object[] {new DefaultMessageSourceResolvable("label.link")},
          null);
      return;
    }
    String sourceGlobalId =
        aef.getParentGlobalId() != null
            ? aef.getParentGlobalId()
            : (aef.getParentRecordInfo() != null ? aef.getParentRecordInfo().getGlobalId() : null);
    errors.pushNestedPath("link");
    try {
      linkValidator.validate(aef.getLink(), sourceGlobalId, errors);
    } finally {
      errors.popNestedPath();
    }
  }

  public boolean createDeleteRequestedExtraFieldsInDatabaseSample(
      ApiSampleWithoutSubSamples apiSample, SampleEntity sample, User user) {
    return createDeleteRequestedExtraFields(
        apiSample.getExtraFields(), sample.getActiveExtraFields(), sample, user);
  }

  public boolean createDeleteRequestedExtraFieldsInDatabaseSubSample(
      ApiSubSample apiSubSample, SubSample subSample, User user) {
    return createDeleteRequestedExtraFields(
        apiSubSample.getExtraFields(), subSample.getActiveExtraFields(), subSample, user);
  }

  public boolean createDeleteRequestedExtraFieldsInDatabaseContainer(
      ApiContainer apiContainer, Container container, User user) {
    return createDeleteRequestedExtraFields(
        apiContainer.getExtraFields(), container.getActiveExtraFields(), container, user);
  }

  public boolean createDeleteRequestedExtraFieldsInDatabaseInstrument(
      ApiInstrumentEntity apiInstrument, InstrumentEntity instrument, User user) {
    return createDeleteRequestedExtraFields(
        apiInstrument.getExtraFields(), instrument.getActiveExtraFields(), instrument, user);
  }

  /**
   * Creates the extra-fields supplied when an Inventory record is first created (POST), Link fields
   * included, so that a record created together with a link persists that link. Mirrors the
   * new-field branch of {@link #createDeleteRequestedExtraFields}: without this the create path
   * built the field but dropped its {@link com.researchspace.model.inventory.field.InventoryLink}
   * (RSDEV-1131), because only the update (PUT) path routed new fields through the link-aware
   * creator.
   */
  public void addExtraFieldsForNewInventoryRecord(
      List<ApiExtraField> incomingFields, InventoryRecord parentInvRec, User user) {
    if (CollectionUtils.isEmpty(incomingFields)) {
      return;
    }
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        null, incomingFields);
    for (ApiExtraField apiField : incomingFields) {
      addRecordExtraFieldForIncomingApiField(apiField, user, parentInvRec);
    }
  }

  boolean createDeleteRequestedExtraFields(
      List<ApiExtraField> incomingFields,
      List<ExtraField> dbFields,
      InventoryRecord parentInvRec,
      User user) {

    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        null, incomingFields);
    boolean changed = applyExistingLinkFieldChanges(incomingFields, dbFields, user);
    if (!CollectionUtils.isEmpty(incomingFields)) {
      for (ApiExtraField apiField : incomingFields) {
        if (apiField.isNewFieldRequest()) {
          addRecordExtraFieldForIncomingApiField(apiField, user, parentInvRec);
          changed = true;
        }
        if (apiField.isDeleteFieldRequest()) {
          if (apiField.getId() == null) {
            throw new ApiRuntimeException("errors.inventory.field.deleteRequestIdMissing");
          }
          Optional<ExtraField> dbFieldOpt =
              dbFields.stream().filter(sf -> apiField.getId().equals(sf.getId())).findFirst();
          if (!dbFieldOpt.isPresent()) {
            throw new ApiRuntimeException(
                "errors.inventory.field.deleteRequestIdUnknown", apiField.getId());
          }
          ExtraField dbField = dbFieldOpt.get();
          softDeleteLinkIfPresent(dbField, user);
          dbField.setDeleted(true);
          changed = true;
        }
      }
    }
    if (changed) {
      parentInvRec.refreshActiveExtraFields();
    }
    return changed;
  }

  /**
   * Soft-deletes the {@link InventoryLink} backing a Link extra-field when that field is itself
   * being soft-deleted, so the link row (and its Envers audit trail) stays in step with the field.
   * A field soft-delete only flips the field's {@code deleted} flag: being an ordinary update
   * rather than a JPA remove it does not trigger the {@code cascade}/{@code orphanRemoval} on
   * {@code ExtraLinkField#link}, and it never dereferences the link. Without this the link row
   * would linger with {@code deleted=false} after its parent field is gone. (The
   * structured-link-field clear path differs deliberately: clearing a value dereferences the row,
   * which the orphanRemoval mapping hard-deletes at flush; see {@code
   * SampleApiManagerImpl#applyLinkFieldValue}.) No-op for non-link fields or a link field that has
   * no link yet.
   */
  private void softDeleteLinkIfPresent(ExtraField dbField, User user) {
    if (dbField instanceof ExtraLinkField) {
      InventoryLink link = ((ExtraLinkField) dbField).getLink();
      if (link != null) {
        inventoryLinkManager.deleteLink(link, user);
      }
    }
  }

  /**
   * Applies target and version-pin changes to existing Link extra-fields. The DTO apply loop
   * ({@code ApiExtraField#applyChangesToDatabaseExtraField}) cannot reach the service-layer {@link
   * InventoryLinkManager}, so a retargeted link would otherwise be silently dropped and the
   * previous target returned on save. Changes go through {@link InventoryLinkManager#updateLink},
   * which validates the new target (existence + readability) and recaptures the pinned audit
   * revision; an extra-field whose link is still unset gains its first link via {@link
   * InventoryLinkManager#createLink}. Relation-type-only changes are left to the DTO apply loop.
   */
  boolean applyExistingLinkFieldChanges(
      List<ApiExtraField> incomingFields, List<ExtraField> dbFields, User user) {
    if (CollectionUtils.isEmpty(incomingFields)) {
      return false;
    }
    boolean changed = false;
    for (ApiExtraField apiField : incomingFields) {
      if (apiField.isNewFieldRequest()
          || apiField.isDeleteFieldRequest()
          || apiField.getId() == null) {
        continue;
      }
      ApiInventoryLink apiLink = apiField.getLink();
      if (apiLink == null || StringUtils.isBlank(apiLink.getTargetGlobalId())) {
        continue;
      }
      Optional<ExtraField> dbFieldOpt =
          dbFields.stream().filter(f -> apiField.getId().equals(f.getId())).findFirst();
      if (!dbFieldOpt.isPresent() || !(dbFieldOpt.get() instanceof ExtraLinkField)) {
        continue;
      }
      ExtraLinkField dbLinkField = (ExtraLinkField) dbFieldOpt.get();
      // the controller-layer validator is skipped when the payload omits "type",
      // so the self-link and relation-type rules must also hold on this service-layer path
      rejectSelfLink(apiLink, dbLinkField);
      assertRelationTypeValid(apiLink);
      InventoryLink dbLink = dbLinkField.getLink();
      if (dbLink == null) {
        dbLinkField.setLink(inventoryLinkManager.createLink(apiLink, user));
        changed = true;
      } else if (linkChanged(apiLink, dbLink)) {
        inventoryLinkManager.updateLink(dbLink, apiLink, user);
        changed = true;
      }
    }
    return changed;
  }

  private void rejectSelfLink(ApiInventoryLink apiLink, ExtraLinkField dbLinkField) {
    GlobalIdentifier target = parseTargetOrNull(apiLink.getTargetGlobalId());
    if (target != null
        && InventoryLinkValidator.isSelfLink(
            target, dbLinkField.getConnectedRecordGlobalIdentifier())) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.selfLinkForbidden", apiLink.getTargetGlobalId());
    }
  }

  /**
   * Rejects a relation type that is not in the DataCite vocabulary. The controller-layer {@link
   * #validate} only runs its LINK branch when the incoming DTO carries {@code type="link"}, and a
   * relation-type-only change does not route through {@link InventoryLinkManager#updateLink}, so
   * without this guard a client that omits {@code type} could persist an invalid relation type via
   * the DTO apply loop. A null relation is left alone: the DTO loop only writes a non-null change,
   * so it cannot introduce an invalid value.
   */
  private void assertRelationTypeValid(ApiInventoryLink apiLink) {
    String relationType = apiLink.getRelationType();
    if (relationType != null && !DataCiteRelationType.isValid(relationType)) {
      throw new ApiRuntimeException("errors.inventory.field.linkRelationTypeInvalid", relationType);
    }
  }

  /**
   * True when the incoming payload points at a different target or version than the stored link.
   * Compared on the parsed base id plus the effective pin (a "vN" suffix counts as a pin), so a
   * client that pins via the suffix with {@code versionPin: null} does not churn an unchanged row
   * with spurious updates. An unparseable incoming id counts as changed so the manager rejects it
   * with its clean validation error.
   */
  private boolean linkChanged(ApiInventoryLink apiLink, InventoryLink dbLink) {
    GlobalIdentifier incoming = parseTargetOrNull(apiLink.getTargetGlobalId());
    if (incoming == null) {
      return true;
    }
    Long effectivePin =
        apiLink.derivedVersionPin() != null ? apiLink.derivedVersionPin() : apiLink.getVersionPin();
    return incoming.getPrefix() != dbLink.getTargetPrefix()
        || !Objects.equals(incoming.getDbId(), dbLink.getTargetDbId())
        || !Objects.equals(effectivePin, dbLink.getVersionPin());
  }

  private GlobalIdentifier parseTargetOrNull(String targetGlobalId) {
    try {
      return new GlobalIdentifier(targetGlobalId);
    } catch (IllegalArgumentException | NullPointerException ex) {
      return null;
    }
  }

  private void addRecordExtraFieldForIncomingApiField(
      ApiExtraField apiField, User user, InventoryRecord parentInvRec) {
    ExtraField newField;
    if (apiField.getTypeAsFieldType() == FieldType.LINK) {
      newField = buildExtraLinkField(apiField, user, parentInvRec);
    } else {
      newField =
          recordFactory.createExtraField(
              apiField.getName(), apiField.getTypeAsFieldType(), user, parentInvRec);
      newField.setData(apiField.getContent());
    }
    parentInvRec.addExtraField(newField); // update parent's field list
  }

  private ExtraLinkField buildExtraLinkField(
      ApiExtraField apiField, User user, InventoryRecord parentInvRec) {
    ExtraLinkField linkField = new ExtraLinkField();
    linkField.setInventoryRecord(parentInvRec);
    if (!StringUtils.isBlank(apiField.getName())) {
      linkField.setName(apiField.getName());
    }
    linkField.setCreatedBy(user.getUsername());
    linkField.setModifiedBy(user.getUsername());
    ApiInventoryLink apiLink = apiField.getLink();
    if (apiLink != null) {
      InventoryLink persisted = inventoryLinkManager.createLink(apiLink, user);
      linkField.setLink(persisted);
    }
    return linkField;
  }
}
