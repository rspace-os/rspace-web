package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.record.IRecordFactory;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
          errors, "name", "errors.required", "name is required");
    }
    ExtraField extraField = recordFactory.createExtraField(aef.getTypeAsFieldType());
    String validationMsg = extraField.validateNewData(aef.getContent());
    if (!StringUtils.isEmpty(validationMsg)) {
      errors.rejectValue("content", "", validationMsg);
    }
  }

  public boolean createDeleteRequestedExtraFieldsInDatabaseSample(
      ApiSampleWithoutSubSamples apiSample, Sample sample, User user) {
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

  private boolean createDeleteRequestedExtraFields(
      List<ApiExtraField> incomingFields,
      List<ExtraField> dbFields,
      InventoryRecord parentInvRec,
      User user) {

    boolean changed = false;
    if (!CollectionUtils.isEmpty(incomingFields)) {
      for (ApiExtraField apiField : incomingFields) {
        if (apiField.isNewFieldRequest()) {
          addRecordExtraFieldForIncomingApiField(apiField, user, parentInvRec);
          changed = true;
        }
        if (apiField.isDeleteFieldRequest()) {
          if (apiField.getId() == null) {
            throw new IllegalArgumentException(
                "'id' property not provided "
                    + "for an extra field with 'deleteFieldRequest' flag");
          }
          Optional<ExtraField> dbFieldOpt =
              dbFields.stream().filter(sf -> apiField.getId().equals(sf.getId())).findFirst();
          if (!dbFieldOpt.isPresent()) {
            throw new IllegalArgumentException(
                "Extra field id: "
                    + apiField.getId()
                    + " doesn't match id of any pre-existing extra field");
          }
          dbFieldOpt.get().setDeleted(true);
          changed = true;
        }
      }
    }
    if (changed) {
      parentInvRec.refreshActiveExtraFields();
    }
    return changed;
  }

  private void addRecordExtraFieldForIncomingApiField(
      ApiExtraField apiField, User user, InventoryRecord parentInvRec) {
    ExtraField newField =
        recordFactory.createExtraField(
            apiField.getName(), apiField.getTypeAsFieldType(), user, parentInvRec);
    newField.setData(apiField.getContent());
    parentInvRec.addExtraField(newField); // update parent's field list
  }
}
