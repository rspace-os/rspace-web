package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InventoryExportApiController.ApiInventoryExportSettingsPost;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class InventoryExportPostValidator implements Validator {

  @Autowired
  @Qualifier("validator")
  private Validator annnotationValidator;

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryExportSettingsPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryExportSettingsPost exportSettings = (ApiInventoryExportSettingsPost) target;
    annnotationValidator.validate(exportSettings, errors);

    if (CollectionUtils.isEmpty(exportSettings.getGlobalIds())
        && CollectionUtils.isEmpty(exportSettings.getUsernamesToExport())) {
      errors.rejectValue("", "errors.inventory.export.request.has.no.ids.no.users");
    }
    if (CollectionUtils.isNotEmpty(exportSettings.getGlobalIds())
        && CollectionUtils.isNotEmpty(exportSettings.getUsernamesToExport())) {
      errors.rejectValue("", "errors.inventory.export.request.has.both.ids.and.users");
    }

    if (!CollectionUtils.isEmpty(exportSettings.getGlobalIds())) {
      List<String> incorrectGlobalIds = new ArrayList<>();
      for (String globalId : exportSettings.getGlobalIds()) {
        if (!GlobalIdentifier.isValid(globalId)) {
          incorrectGlobalIds.add(globalId);
        }
      }
      if (!incorrectGlobalIds.isEmpty()) {
        errors.rejectValue(
            "globalIds",
            "errors.inventory.export.request.invalid.globalIds",
            new Object[] {StringUtils.join(incorrectGlobalIds, ", ")},
            "incorrect globalIds");
      }
    }
  }
}
