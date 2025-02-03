package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InventoryAttachmentsApiController.ApiInventoryAttachmentPost;
import com.researchspace.model.core.GlobalIdentifier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class InventoryAttachmentPostValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryAttachmentPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryAttachmentPost attachmentSettings = (ApiInventoryAttachmentPost) target;

    if (StringUtils.isBlank(attachmentSettings.getParentGlobalId())) {
      errors.rejectValue("parentGlobalId", "errors.inventory.attachment.parentGlobalId.empty");
    }
    if (!GlobalIdentifier.isValid(attachmentSettings.getParentGlobalId())) {
      errors.rejectValue("parentGlobalId", "errors.inventory.attachment.parentGlobalId.invalid");
    }
    if (StringUtils.isBlank(attachmentSettings.getMediaFileGlobalId())) {
      errors.rejectValue(
          "mediaFileGlobalId", "errors.inventory.attachment.mediaFileGlobalId.empty");
    }
    if (!GlobalIdentifier.isValid(attachmentSettings.getMediaFileGlobalId())) {
      errors.rejectValue(
          "mediaFileGlobalId", "errors.inventory.attachment.mediaFileGlobalId.invalid");
    }
  }
}
