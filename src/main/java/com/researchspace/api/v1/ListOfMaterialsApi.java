/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.model.User;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/inventory/v1/listOfMaterials")
public interface ListOfMaterialsApi {

  /** */
  @GetMapping(path = "/forDocument/{docId}")
  List<ApiListOfMaterials> getListOfMaterialsForDocument(Long docId, User user)
      throws BindException;

  /** */
  @GetMapping(path = "/forField/{elnFieldId}")
  List<ApiListOfMaterials> getListOfMaterialsForField(Long elnFieldId, User user)
      throws BindException;

  /** */
  @GetMapping(path = "/forInventoryItem/{globalId}")
  List<ApiListOfMaterials> getListOfMaterialsForInventoryItem(String globalId, User user)
      throws BindException;

  /** */
  @GetMapping(path = "/{lomId}")
  ApiListOfMaterials getListOfMaterialsById(Long lomId, User user) throws BindException;

  /** */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiListOfMaterials addListOfMaterials(
      @RequestBody @Valid ApiListOfMaterials newList, BindingResult errors, User user)
      throws BindException;

  /** */
  @PutMapping(value = "/{lomId}")
  ApiListOfMaterials updateListOfMaterials(
      Long lomId,
      @RequestBody @Valid ApiListOfMaterials listUpdate,
      BindingResult errors,
      User user)
      throws BindException;

  /** */
  @DeleteMapping(value = "/{lomId}")
  ApiListOfMaterials deleteListOfMaterials(Long lomId, User user);

  /** */
  @GetMapping(value = "/{lomId}/canEdit")
  boolean canUserEditListOfMaterials(Long lomId, User user);

  /** */
  @GetMapping(value = "/{lomId}/revisions/{revisionId}")
  ApiListOfMaterials getListOfMaterialsRevision(Long lomId, Long revisionId, User user);
}
