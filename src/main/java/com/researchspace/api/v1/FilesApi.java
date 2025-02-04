/**
 * RSpace API Access your RSpace documents programmatically. All requests require authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ApiFileSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiFileSearchResult;
import com.researchspace.model.User;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1/files")
public interface FilesApi {

  @GetMapping
  ApiFileSearchResult getFiles(
      DocumentApiPaginationCriteria pgCrit,
      ApiFileSearchConfig srchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiFile uploadFiles(
      Long folderId, String caption, Long originalImageId, MultipartFile file, User user)
      throws BindException, IOException, URISyntaxException;

  /**
   * Replaces the file associated with mediaFileId with a new version
   *
   * @param id
   * @param file
   * @param user
   * @return a new ApiFile representing the new file version
   * @throws BindException
   * @throws IOException
   * @throws URISyntaxException
   */
  @PostMapping("/{id}/file")
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiFile updateFile(Long id, MultipartFile file, User user)
      throws BindException, IOException, URISyntaxException;

  @GetMapping("/{id}")
  ApiFile getFileById(@PathVariable Long id, User user);

  @GetMapping("/{id}/file")
  void getFileBytes(@PathVariable Long id, User user, HttpServletResponse response)
      throws IOException;
}
