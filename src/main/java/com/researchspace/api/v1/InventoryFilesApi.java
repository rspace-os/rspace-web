package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFileImageRequest;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.model.User;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * RSpace Inventory API Access your Inventory documents programmatically. All requests require
 * authentication.
 */
@RequestMapping("/api/inventory/v1/files")
public interface InventoryFilesApi {

  @GetMapping("/{id}")
  ApiInventoryFile getFileById(@PathVariable Long id, User user);

  @GetMapping("/{id}/file")
  void getFileBytes(@PathVariable Long id, User user, HttpServletResponse response)
      throws IOException;

  /**
   * This method retrieves the bytes of the image representation of the requested file Currently
   * limited to just chemical files.
   *
   * @param id the file id of the file to get the image for
   * @param imageParams the parameters for which to generate/get the image
   * @param user the user
   * @param response the response returned by this endpoint
   * @throws IOException
   * @throws URISyntaxException
   */
  @GetMapping("/{id}/file/image")
  void getImageBytes(
      @PathVariable Long id,
      ApiInventoryFileImageRequest imageParams,
      User user,
      HttpServletResponse response)
      throws IOException, URISyntaxException;

  @GetMapping("{id}/chemFileDetails")
  @ResponseBody
  AjaxReturnObject<ChemEditorInputDto> getChemFileDto(@PathVariable Long id, User user)
      throws IOException;

  @PostMapping
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiInventoryFile uploadFile(MultipartFile file, ApiInventoryFilePost settings, User user)
      throws IOException, BindException;

  @DeleteMapping(value = "/{id}")
  ApiInventoryFile deleteFile(Long id, User user);

  @GetMapping("/image/{contentsHash}")
  ResponseEntity<byte[]> getImageByContentsHash(
      @PathVariable String contentsHash, @RequestAttribute(name = "user") User user) throws IOException;
}
