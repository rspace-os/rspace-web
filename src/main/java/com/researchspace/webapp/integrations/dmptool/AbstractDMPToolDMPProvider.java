package com.researchspace.webapp.integrations.dmptool;

import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

  protected DMPUser saveJsonDMP(DMPToolDMP dmp, String title, User user, String json)
      throws IOException {
    EcatDocumentFile dmpJson = null;
    String abbreviatedTitle = abbreviateTitle(title);
    try (InputStream is = new ByteArrayInputStream(json.getBytes())) {
      dmpJson = mediaManager.saveNewDMP(abbreviatedTitle + ".json", is, user, null);
    }
    return saveNewDMP(dmp, abbreviatedTitle, user, dmpJson);
  }

  private String abbreviateTitle(String title) {
    return title.length() > 3 ? StringUtils.abbreviate(title, 250) : title;
  }

  private DMPUser saveNewDMP(DMPToolDMP dmp, String title, User user, EcatDocumentFile dmpJson) {
    Optional<DMPUser> dmpUser = dmpManager.findByDmpId(dmp.getId() + "", user);
    if (dmpUser.isEmpty()) {
      dmpUser =
          Optional.of(
              new DMPUser(
                  user,
                  new DmpDto(
                      dmp.getId() + "",
                      title,
                      DMPSource.DMP_TOOL,
                      dmp.getDoiLink(),
                      dmp.getDmpLink())));
    }
    if (dmpJson != null) {
      dmpUser.get().setDmpDownloadFile(dmpJson);
    } else {
      log.warn("Unexpected null DMP Json - did download work?");
    }
    return dmpManager.save(dmpUser.get());
  }

  abstract String getJson(DMPToolDMP dmp, String accessToken)
      throws URISyntaxException, MalformedURLException;

  boolean assertIsNewDMP(DMPToolDMP dmp, User user) {
    return !dmpManager.findByDmpId(dmp.getId() + "", user).isPresent();
  }
}
