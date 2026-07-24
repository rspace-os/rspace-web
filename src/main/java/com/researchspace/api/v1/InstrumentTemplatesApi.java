/**
 * RSpace Inventory API Access your RSpace Inventory Instrument Templates programmatically. All
 * requests require authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/inventory/v1/instrumentTemplates")
public interface InstrumentTemplatesApi {

  @GetMapping
  ApiInstrumentTemplateSearchResult getTemplatesForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @GetMapping(path = "/{id}")
  ApiInstrumentTemplate getInstrumentTemplateById(Long id, User user);

  @GetMapping(path = "/{id}/versions/{version}")
  ApiInstrumentTemplate getInstrumentTemplateVersionById(Long id, Long version, User user);

  @GetMapping(value = "/{id}/revisions")
  ApiInventoryRecordRevisionList getInstrumentTemplateAllRevisions(Long id, User user);

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiInstrumentTemplate createNewInstrumentTemplate(
      @RequestBody @Valid ApiInstrumentTemplatePost templatePost, BindingResult errors, User user)
      throws BindException;

  @PutMapping(value = "/{id}")
  ApiInstrumentTemplate updateInstrumentTemplate(
      Long id, @RequestBody @Valid ApiInstrumentTemplate template, BindingResult errors, User user)
      throws BindException;

  @DeleteMapping(value = "/{id}")
  ApiInstrumentTemplate deleteInstrumentTemplate(Long id, User user);

  @PutMapping(value = "/{id}/restore")
  ApiInstrumentTemplate restoreDeletedInstrumentTemplate(Long id, User user);

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getInstrumentTemplateImage(Long id, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getInstrumentTemplateThumbnail(Long id, User user) throws IOException;

  @PostMapping(path = "/{templateId}/icon")
  @ResponseStatus(HttpStatus.CREATED)
  ApiInstrumentEntityInfo setIcon(Long templateId, MultipartFile iconFile, User user)
      throws BindException, IOException;

  @GetMapping(path = "/{templateId}/icon/{iconId}")
  void getIcon(Long templateId, Long iconId, User user, HttpServletResponse response)
      throws IOException;

  @PutMapping(value = "/{id}/actions/changeOwner")
  ApiInstrumentTemplate changeInstrumentTemplateOwner(
      Long id, @RequestBody @Valid ApiInstrumentTemplate template, BindingResult errors, User user)
      throws BindException;

  @PostMapping("/{id}/actions/updateInstrumentsToLatestTemplateVersion")
  ApiInventoryBulkOperationResult updateInstrumentsToLatestTemplateVersion(Long id, User user);
}
