/**
 * RSpace API Access your RSpace Gallery files programmatically. All requests require authentication
 * using an API key set as the value of the header `RSpace-API-Key`.
 */
package com.researchspace.api.v1;

import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@RequestMapping("/api/v1/gallery")
public interface GalleryApi {

  @GetMapping("/{mediaFileId}/download")
  @ResponseBody
  void downloadGalleryFile(Long mediaFileId, User user, HttpServletResponse response)
      throws IOException;
}
