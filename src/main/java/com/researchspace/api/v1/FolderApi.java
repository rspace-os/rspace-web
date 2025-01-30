package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiRecordTreeItemListing;
import com.researchspace.model.User;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/v1/folders")
public interface FolderApi {
  /**
   * Creates a new folder
   *
   * @param folder
   * @param errors
   * @param user
   * @return
   * @throws BindException
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiFolder createNewFolder(@RequestBody @Valid ApiFolder folder, BindingResult errors, User user)
      throws BindException;

  /**
   * Gets a folder by given Id
   *
   * @param id
   * @param includePathToRootFolder
   * @param user
   * @return
   * @throws NotFoundException if folder does not exist
   */
  @GetMapping(value = "/{id}")
  ApiFolder getFolder(@PathVariable Long id, Boolean includePathToRootFolder, User user);

  /**
   * Deletes a folder or notebook by ID
   *
   * @param id ID of folder or notebook to be deleted
   * @param user
   * @return
   */
  @DeleteMapping(value = "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteFolder(@PathVariable Long id, User user);

  /**
   * Lists root folder
   *
   * @param recordTypes empty list, null or or list of any of "notebook", "folder", "document"
   * @param pgCrit
   * @param errors
   * @param user
   * @return
   * @throws BindException if pagination criteria are invalid
   */
  @GetMapping(value = "/tree")
  ApiRecordTreeItemListing rootFolderTree(
      Set<String> recordTypes,
      DocumentApiPaginationCriteria pgCrit,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Lists contents of folder identified by {id}
   *
   * @param recordTypes empty list, null or or list of any of "notebook", "folder", "document"
   * @param pgCrit
   * @param errors
   * @param user
   * @return
   * @throws BindException if pagination criteria are invalid
   */
  @GetMapping(value = "/tree/{id}")
  ApiRecordTreeItemListing folderTreeById(
      @PathVariable Long id,
      Set<String> recordTypes,
      DocumentApiPaginationCriteria pgCrit,
      BindingResult errors,
      User user)
      throws BindException;
}
