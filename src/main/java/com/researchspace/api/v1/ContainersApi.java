/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
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

@RequestMapping("/api/inventory/v1/containers")
public interface ContainersApi {

  @GetMapping
  ApiContainerSearchResult getTopContainersForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiContainer getContainerById(Long id, Boolean includeContent, User user);

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiContainer createNewContainer(
      @RequestBody @Valid ApiContainer container, BindingResult errors, User user)
      throws BindException;

  @PutMapping(value = "/{id}")
  ApiContainer updateContainer(
      Long id, @RequestBody @Valid ApiContainer containerUpdate, BindingResult errors, User user)
      throws BindException;

  @GetMapping("/{id}/image/{unused}")
  ResponseEntity<byte[]> getContainerImage(Long id, User user) throws IOException;

  @GetMapping("/{id}/thumbnail/{unused}")
  ResponseEntity<byte[]> getContainerThumbnail(Long id, User user) throws IOException;

  @GetMapping("/{id}/locationsImage/{unused}")
  ResponseEntity<byte[]> getContainerLocationsImage(Long id, User user) throws IOException;

  @DeleteMapping(value = "/{id}")
  ApiContainer deleteContainer(Long id, User user);

  @PutMapping(value = "/{id}/restore")
  ApiContainer restoreDeletedContainer(Long id, User user);

  @PutMapping(value = "/{id}/actions/changeOwner")
  ApiContainer changeContainerOwner(
      Long id, @RequestBody @Valid ApiContainer containerUpdate, BindingResult errors, User user)
      throws BindException;

  @PostMapping("/{id}/actions/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  ApiContainer duplicate(Long id, User user);
}
