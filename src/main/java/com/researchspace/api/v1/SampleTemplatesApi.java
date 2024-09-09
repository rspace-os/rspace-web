/**
 * RSpace Inventory API Access your RSpace Inventory Sample Templates programmatically. All requests
 * require authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiFormSearchResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleTemplateSearchResult;
import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
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

@RequestMapping("/api/inventory/v1/sampleTemplates")
public interface SampleTemplatesApi {

  /**
   * Lists sample templates visible to the user making the request, searching for '%name%' or
   * '%tag%'
   *
   * @param pgCrit
   * @param searchConfig
   * @param errors
   * @param user
   * @returnAn {@link ApiFormSearchResult}
   * @throws BindException
   */
  @GetMapping
  ApiSampleTemplateSearchResult getTemplatesForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @GetMapping(path = "/{id}")
  ApiSampleTemplate getSampleTemplateById(Long id, User user);

  @GetMapping(value = "/{id}/versions/{version}")
  ApiSampleTemplate getSampleTemplateVersionById(Long id, Long version, User user);

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleTemplate createNewSampleTemplate(
      @RequestBody @Valid ApiSampleTemplatePost formPost, BindingResult errors, User user)
      throws BindException;

  @PutMapping(value = "/{id}")
  ApiSampleTemplate updateSampleTemplate(
      Long id, @RequestBody @Valid ApiSampleTemplate template, BindingResult errors, User user)
      throws BindException;

  @DeleteMapping(value = "/{id}")
  ApiSample deleteSampleTemplate(Long id, User user);

  @PutMapping(value = "/{id}/restore")
  ApiSample restoreDeletedSampleTemplate(Long id, User user);

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getSampleTemplateImage(Long id, User user) throws IOException;

  @GetMapping("/image/{fileName}")
  ResponseEntity<byte[]> getImageByFileName(String fileName, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getSampleTemplateThumbnail(Long id, User user) throws IOException;

  @GetMapping("/thumbnail/{fileName}")
  ResponseEntity<byte[]> getThumbnailByFileName(String fileName, User user) throws IOException;

  /**
   * Set template icon
   *
   * @param templateId
   * @param user
   * @return
   * @throws BindException
   * @throws IOException
   */
  @PostMapping(path = "/{templateId}/icon")
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleInfo setIcon(Long templateId, MultipartFile iconFile, User user)
      throws BindException, IOException;

  @GetMapping(path = "/{templateId}/icon/{iconId}")
  void getIcon(Long templateId, Long iconId, User user, HttpServletResponse response)
      throws IOException;

  @PutMapping(value = "/{id}/actions/changeOwner")
  ApiSampleTemplate changeSampleTemplateOwner(
      Long id, @RequestBody @Valid ApiSampleTemplate template, BindingResult errors, User user)
      throws BindException;

  @PostMapping("/{id}/actions/updateSamplesToLatestTemplateVersion")
  ApiInventoryBulkOperationResult updateSamplesToLatestTemplateVersion(Long id, User user);
}
