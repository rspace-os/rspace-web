package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.InventoryAttachmentsApi;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.service.inventory.InventoryFileApiManager;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class InventoryAttachmentsApiController extends BaseApiInventoryController
    implements InventoryAttachmentsApi {

  @Autowired private InventoryFileApiManager invFileManager;

  @Autowired private InventoryAttachmentPostValidator invAttachmentPostValidator;

  @Data
  @NoArgsConstructor
  public static class ApiInventoryAttachmentPost {

    @JsonProperty("parentGlobalId")
    private String parentGlobalId;

    @JsonProperty("mediaFileGlobalId")
    private String mediaFileGlobalId;
  }

  @Override
  public ApiInventoryFile attachMediaFileToInventoryItem(
      @RequestBody ApiInventoryAttachmentPost settings, @RequestAttribute(name = "user") User user)
      throws BindException {

    // validate incoming settings
    BindingResult errors = new BeanPropertyBindingResult(settings, "attachmentDetails");
    inputValidator.validate(settings, invAttachmentPostValidator, errors);
    throwBindExceptionIfErrors(errors);

    // attach as an inventory file
    GlobalIdentifier parentInvRecordGlobalId = new GlobalIdentifier(settings.getParentGlobalId());
    assertUserCanEditInventoryRecord(parentInvRecordGlobalId, user);
    GlobalIdentifier mediaFileGlobalId = new GlobalIdentifier(settings.getMediaFileGlobalId());
    assertUserCanReadInventoryRecord(parentInvRecordGlobalId, user);
    InventoryFile invFile =
        invFileManager.attachGalleryFileToInventoryRecord(
            parentInvRecordGlobalId, mediaFileGlobalId, user);

    // return as inventory file
    ApiInventoryFile apiFile = new ApiInventoryFile(invFile);
    addInventoryFileLink(apiFile);
    return apiFile;
  }
}
