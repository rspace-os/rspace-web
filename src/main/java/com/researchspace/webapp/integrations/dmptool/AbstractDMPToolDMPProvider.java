package com.researchspace.webapp.integrations.dmptool;

import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** Common methods to both stub and real implementations */
@Slf4j
public abstract class AbstractDMPToolDMPProvider implements DMPToolDMPProvider {
  URL baseUrl;
  URL apiBaseUrl;

  @Autowired UserManager userManager;
  @Autowired MediaManager mediaManager;
  @Autowired DMPManager dmpManager;

  public AbstractDMPToolDMPProvider(URL baseUrl) {
    this.baseUrl = baseUrl;
    try {
      this.apiBaseUrl = new URL(baseUrl, "/api/v2/");
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Couldn't create DMP baseURL " + e.getMessage());
    }
  }

  DMPUser saveDMPPdf(DMPToolDMP dmp, String title, User user, byte[] pdfBytes) throws IOException {
    EcatDocumentFile dmpPDF = null;
    String abbreviatedTitle = abbreviateTitle(title);
    try (InputStream in = new ByteArrayInputStream(pdfBytes)) {
      dmpPDF = mediaManager.saveNewDMP(abbreviatedTitle + ".pdf", in, user, null);
    }
    return saveNewDMP(dmp, abbreviatedTitle, user, dmpPDF);
  }

  private String abbreviateTitle(String title) {
    return title.length() > 3 ? StringUtils.abbreviate(title, 250) : title;
  }

  DMPUser saveNewDMP(DMPToolDMP dmp, String title, User user, EcatDocumentFile dmpPDF) {
    var dmpUser =
        dmpManager
            .findByDmpId(dmp.getId() + "", user)
            .orElse(new DMPUser(user, new DMP(dmp.getId() + "", title)));
    if (dmpPDF != null) {
      dmpUser.setDmpDownloadPdf(dmpPDF);
    } else {
      log.warn("Unexpected null DMP PDF - did download work?");
    }
    return dmpManager.save(dmpUser);
  }

  abstract byte[] getPdfBytes(DMPToolDMP dmp, String accessToken)
      throws URISyntaxException, MalformedURLException;

  boolean assertIsNewDMP(DMPToolDMP dmp, User user) {
    return !dmpManager.findByDmpId(dmp.getId() + "", user).isPresent();
  }
}
