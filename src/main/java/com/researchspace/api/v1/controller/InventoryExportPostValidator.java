package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InventoryExportApiController.ApiInventoryExportSettingsPost;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.ListFormatUtils;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
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
      errors.reject("errors.inventory.export.requestHasNoIdsNoUsers");
    }
    if (CollectionUtils.isNotEmpty(exportSettings.getGlobalIds())
        && CollectionUtils.isNotEmpty(exportSettings.getUsernamesToExport())) {
      errors.reject("errors.inventory.export.requestHasBothIdsAndUsers");
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
            "errors.inventory.export.requestInvalidGlobalIds",
            new Object[] {ListFormatUtils.formatList(incorrectGlobalIds)},
            null);
      }
    }
  }
}
