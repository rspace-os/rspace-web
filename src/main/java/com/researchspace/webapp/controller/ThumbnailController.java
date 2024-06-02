package com.researchspace.webapp.controller;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.service.AuditManager;
import com.researchspace.service.ThumbnailManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Gets and creates thumbnail images for document/gallery views; creating */
@Controller
@RequestMapping({"/thumbnail", "/public/publicView/thumbnail"})
public class ThumbnailController extends BaseController {

  @Autowired @Setter // for tests
  private ThumbnailManager thumbnailManager;

  @Autowired private AuditManager auditManager;

  // As of RSPace 1.56, there is no caching of images returned from here
  // chem images have same url even if underlying chem structure has changed, as the sourceId
  // remains the same.
  // Images can be safely cached
  // different sized images are stored with different height/width; and uploading a new version uses
  // a new sourceId,
  // so the URL is different.
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @GetMapping("/data")
  public ResponseEntity<byte[]> getThumbnail(Thumbnail query)
      throws IOException, URISyntaxException {

    log.info("Query is {}", query);
    User subject = userManager.getAuthenticatedUserInSession();
    Thumbnail thumbnail;

    try {
      thumbnail = thumbnailManager.getThumbnail(query, subject);
    } catch (AuthorizationException e) {
      throw new AuthorizationException(getResourceNotFoundMessage("Thumbnail", query.getId()));
    }
    if (thumbnail == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    byte[] data = null;
    final HttpHeaders headers = new HttpHeaders();

    if (SourceType.CHEM.equals(query.getSourceType())) {
      AuditedEntity<RSChemElement> auditedRSChemElement = null;
      if (query.getRevision() != null) {
        auditedRSChemElement =
            auditManager.getObjectForRevision(
                RSChemElement.class, query.getSourceId(), query.getRevision());
      } else {
        auditedRSChemElement =
            auditManager.getNewestRevisionForEntity(RSChemElement.class, query.getSourceId());
      }
      if (auditedRSChemElement != null) {
        RSChemElement rsChemElement = auditedRSChemElement.getEntity();
        data = rsChemElement.getDataImage();
        headers.setContentType(MediaType.IMAGE_PNG);
      }
    }

    if (data == null) {
      data = thumbnailManager.getThumbnailData(thumbnail.getId());
      // Previously thumbnails were generated as jpegs (now png), so this is here to
      // determine what image type header to set for each thumbnail request. In case we get a
      // weird legacy thumbnail format, the exception gets caught and we set the header type
      // to indicate unknown binary content
      try {
        ImageFormat format = Imaging.guessFormat(data);
        String extension = format.getExtension().toLowerCase();
        if (extension.equals("unknown")) headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        else headers.setContentType(MediaType.valueOf("image/" + extension));
      } catch (ImageReadException e) {
        // this gets thrown if Imaging.guessFormat tries to read a corrupted image file I guess,
        // but in reality it should just return a format with extension "unknown" if that happens
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      }
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).mustRevalidate())
        .headers(headers)
        .body(data);
  }
}
