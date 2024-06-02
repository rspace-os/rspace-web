/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import java.io.IOException;
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

@RequestMapping("/api/inventory/v1/samples")
public interface SamplesApi {

  @GetMapping
  ApiSampleSearchResult getSamplesForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleWithFullSubSamples createNewSample(
      @RequestBody @Valid ApiSampleWithFullSubSamples sample, BindingResult errors, User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiSample getSampleById(Long id, User user);

  @GetMapping("/validateNameForNewSample")
  String validateNameForNewSample(String name, User user) throws BindException;

  @PutMapping(value = "/{id}")
  ApiSample updateSample(
      Long id,
      @RequestBody @Valid ApiSampleWithFullSubSamples sample,
      BindingResult errors,
      User user)
      throws BindException;

  @DeleteMapping(value = "/{id}")
  ApiSample deleteSample(Long id, boolean forceDelete, User user);

  @PutMapping(value = "/{id}/restore")
  ApiSample restoreDeletedSample(Long id, User user);

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getSampleImage(Long id, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getSampleThumbnail(Long id, User user) throws IOException;

  @PutMapping(value = "/{id}/actions/changeOwner")
  ApiSample changeSampleOwner(
      Long id,
      @RequestBody @Valid ApiSampleWithFullSubSamples sample,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping("/{id}/actions/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleWithFullSubSamples duplicate(Long id, User user);

  @PostMapping("/{id}/actions/updateToLatestTemplateVersion")
  ApiSample updateToLatestTemplateVersion(Long id, User user);

  @GetMapping(value = "/{id}/revisions")
  ApiInventoryRecordRevisionList getSampleAllRevisions(Long id, User user);

  @GetMapping(value = "/{id}/revisions/{revisionId}")
  ApiSample getSampleRevision(Long id, Long revisionId, User user);
}
