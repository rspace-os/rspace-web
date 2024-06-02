package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.model.User;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

/** Import external representations into RSpace Documents */
@RequestMapping("/api/v1/import")
public interface ImportApi {

  @PostMapping("/word")
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiDocumentInfo importWord(Long folderId, Long imageFolderId, MultipartFile file, User user)
      throws IOException;

  @PostMapping("/evernote")
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiFolder importEvernote(Long folderId, Long imageFolderId, MultipartFile file, User user)
      throws IOException;
}
