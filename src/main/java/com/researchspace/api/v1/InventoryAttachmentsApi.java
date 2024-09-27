package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryAttachmentsApiController.ApiInventoryAttachmentPost;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RSpace Inventory API operate on Gallery Files attached to inventory records. All requests require
 * authentication.
 */
@RequestMapping("/api/inventory/v1/attachments")
public interface InventoryAttachmentsApi {

  @PostMapping
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiInventoryFile attachMediaFileToInventoryItem(ApiInventoryAttachmentPost settings, User user)
      throws BindException;
}
