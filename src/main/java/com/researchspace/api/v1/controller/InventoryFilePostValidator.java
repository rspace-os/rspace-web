package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.model.core.GlobalIdentifier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class InventoryFilePostValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryFilePost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryFilePost fileSettings = (ApiInventoryFilePost) target;

    if (StringUtils.isBlank(fileSettings.getParentGlobalId())) {
      errors.rejectValue("parentGlobalId", "errors.inventory.file.parentGlobalId.empty");
    }
    if (!GlobalIdentifier.isValid(fileSettings.getParentGlobalId())) {
      errors.rejectValue("parentGlobalId", "errors.inventory.file.parentGlobalId.invalid");
    }
  }
}
