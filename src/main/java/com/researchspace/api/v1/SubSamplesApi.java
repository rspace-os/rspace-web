/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.inventory.impl.SubSampleCreateNewConfig;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import java.io.IOException;
import java.util.List;
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

@RequestMapping("/api/inventory/v1/subSamples")
public interface SubSamplesApi {

  @GetMapping
  ApiSubSampleSearchResult getSubSamplesForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  List<ApiSubSample> createNewSubSamplesForSample(
      @RequestBody @Valid SubSampleCreateNewConfig config, BindingResult errors, User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiSubSample getSubSampleById(Long id, User user);

  @PutMapping(value = "/{id}")
  ApiSubSample updateSubSample(
      Long id, @RequestBody @Valid ApiSubSample subSample, BindingResult errors, User user)
      throws BindException;

  @PostMapping(value = "/{id}/notes")
  ApiSubSample addSubSampleNote(
      Long id, @RequestBody @Valid ApiSubSampleNote subSampleNote, BindingResult errors, User user)
      throws BindException;

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getSubSampleImage(Long id, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getSubSampleThumbnail(Long id, User user) throws IOException;

  @PostMapping("/{id}/actions/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  ApiInventoryRecordInfo duplicate(Long id, User user);

  @PostMapping("/{id}/actions/split")
  @ResponseStatus(HttpStatus.CREATED)
  List<ApiSubSample> split(
      Long id, @RequestBody @Valid SubSampleDuplicateConfig config, BindingResult errors, User user)
      throws BindException;

  @DeleteMapping(value = "/{id}")
  ApiSubSample deleteSubSample(Long id, User user);

  @PutMapping(value = "/{id}/restore")
  ApiSubSample restoreDeletedSubSample(Long id, User user);

  @GetMapping(value = "/{id}/revisions")
  ApiInventoryRecordRevisionList getSubSampleAllRevisions(Long id, User user);

  @GetMapping(value = "/{id}/revisions/{revisionId}")
  ApiSubSample getSubSampleRevision(Long id, Long revisionId, User user);
}
