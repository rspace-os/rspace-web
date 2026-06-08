/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
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

@RequestMapping("/api/inventory/v1/instruments")
public interface InstrumentsApi {

  @GetMapping
  ApiInstrumentSearchResult getInstrumentsForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiInstrument createNewInstrument(
      @RequestBody @Valid ApiInstrument instrument, BindingResult errors, User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiInstrument getInstrumentById(Long id, User user);

  @GetMapping("/validateNameForNewInstrument")
  String validateNameForNewInstrument(String name, User user) throws BindException;

  @PutMapping(value = "/{id}")
  ApiInstrument updateInstrument(
      Long id, @RequestBody @Valid ApiInstrument instrument, BindingResult errors, User user)
      throws BindException;

  @DeleteMapping(value = "/{id}")
  ApiInstrument deleteInstrument(Long id, User user);

  @PutMapping(value = "/{id}/restore")
  ApiInstrument restoreDeletedInstrument(Long id, User user);

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getInstrumentImage(Long id, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getInstrumentThumbnail(Long id, User user) throws IOException;

  @PutMapping(value = "/{id}/actions/changeOwner")
  ApiInstrument changeInstrumentOwner(
      Long id, @RequestBody @Valid ApiInstrument instrument, BindingResult errors, User user)
      throws BindException;

  @PostMapping("/{id}/actions/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  ApiInstrument duplicate(Long id, User user);

  @PostMapping("/{id}/actions/updateToLatestTemplateVersion")
  ApiInstrument updateToLatestTemplateVersion(Long id, User user);

  @GetMapping(value = "/{id}/revisions")
  ApiInventoryRecordRevisionList getInstrumentAllRevisions(Long id, User user);

  @GetMapping(value = "/{id}/revisions/{revisionId}")
  ApiInstrument getInstrumentRevision(Long id, Long revisionId, User user);
}
