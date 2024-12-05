package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GalleryApi;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.service.BaseRecordManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@Slf4j
@NoArgsConstructor
@ApiController
public class GalleryApiController extends BaseApiController implements GalleryApi {

  @Autowired private BaseRecordManager baseRecordManager;

  @Autowired
  @Qualifier("compositeFileStore")
  protected FileStore fileStore;

  @Autowired private AuditTrailService auditService;

  @Override
  public void downloadGalleryFile(
      @PathVariable Long mediaFileId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {

    EcatMediaFile mediaFile = baseRecordManager.retrieveMediaFile(user, mediaFileId);
    FileProperty fp = mediaFile.getFileProperty();
    auditService.notify(new GenericEvent(user, mediaFile, AuditAction.DOWNLOAD));

    Optional<FileInputStream> fis = fileStore.retrieve(fp);
    if (!fis.isPresent()) {
      log.error("Could not retrieve file for FileProperty {}", fp.getId());
      throw new IllegalStateException("Could not retrieve file for FileProperty: " + fp.getId());
    }

    response.setContentType("application/octet-stream");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + mediaFile.getFileName() + "\"");
    try (InputStream is = fis.get();
        ServletOutputStream out = response.getOutputStream()) {
      IOUtils.copy(is, out);
    }
  }
}
