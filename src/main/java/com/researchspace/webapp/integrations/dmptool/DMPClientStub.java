package com.researchspace.webapp.integrations.dmptool;

import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.views.ServiceOperationResult;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Stub implementation of DMP web client returns hard-coded DMP lists Use -Ddmp.pdf=/full/path/toPDF
 * to set a PDF to download.
 */
public class DMPClientStub extends AbstractDMPToolDMPProvider implements DMPToolDMPProvider {
  private static final Logger LOG = LoggerFactory.getLogger(DMPClientStub.class);

  @Value("${dmp.pdf}")
  private String pdfPath;

  public DMPClientStub(URL baseUrl) {
    super(baseUrl);
  }

  private DMPToolDMP mkDMP(Long id, String title, String description) {
    var dmp = new DMPToolDMP();
    dmp.setTitle(title);
    dmp.setDescription(description);
    dmp.setLinks(
        Map.of(
            "get", baseUrl.toString() + "/api/v2/plans/" + Long.toString(id),
            "download", baseUrl.toString() + "/api/v2/plans/" + Long.toString(id) + ".pdf"));
    return dmp;
  }

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, String accessToken) {
    return successResult();
  }

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, User user) {
    return successResult();
  }

  @Override
  public DMPUser doJsonDownload(DMPToolDMP dmp, String title, String accessToken)
      throws URISyntaxException, IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServiceOperationResult<DMPUser> doJsonDownload(DMPToolDMP dmp, String title, User user)
      throws URISyntaxException, IOException {
    String jsonString = getJson(dmp, "");
    if (assertIsNewDMP(dmp, user)) {
      return new ServiceOperationResult<>(saveJsonDMP(dmp, title, user, jsonString), true);
    } else {
      return new ServiceOperationResult<>(
          null, false, " DMP with id " + dmp.getId() + " already exists");
    }
  }

  private ServiceOperationResult<String> successResult() {
    return new ServiceOperationResult<>("Success", true);
  }

  String getJson(DMPToolDMP dmp, String accessToken) {
    try (FileInputStream fis = new FileInputStream(pdfPath)) {
      return fis.toString();
    } catch (IOException e) {
      LOG.error("Error creating input stream.", e);
      return "";
    }
  }

  @Override
  public DMPList listPlans(DMPPlanScope scope, String accessToken)
      throws MalformedURLException, URISyntaxException {
    List<DMPToolDMP> mine =
        List.of(
            mkDMP(1L, "DMP title1 ", "DMP 1 description"),
            mkDMP(2L, "DMP title2 ", "DMP 2 description"));
    List<DMPToolDMP> publicDMPs =
        List.of(
            mkDMP(3L, "Public DMP title1 ", "Public DMP 1 description"),
            mkDMP(4L, "Public DMP title2 ", "Public DMP 2 description"));
    DMPList rc = new DMPList();
    switch (scope) {
      case MINE:
        rc.setItems(mine);
        return rc;
      case PUBLIC:
        rc.setItems(publicDMPs);
        return rc;
      case BOTH:
        var both = new ArrayList<>(mine);
        both.addAll(publicDMPs);
        rc.setItems(both);
        return rc;
      default:
        throw new IllegalArgumentException("Unknown scope [" + scope + "]");
    }
  }

  @Override
  public ServiceOperationResult<DMPList> listPlans(DMPPlanScope scope, User user)
      throws MalformedURLException, URISyntaxException {
    return new ServiceOperationResult<>(listPlans(scope, user.getUsername()), true);
  }

  @Override
  public ServiceOperationResult<DMPToolDMP> getPlanById(String dmpId, User user)
      throws MalformedURLException, URISyntaxException {
    var dmp = getPlanById(dmpId, "");
    return new ServiceOperationResult<>(dmp, true);
  }

  @Override
  public DMPToolDMP getPlanById(String dmpId, String accessToken)
      throws MalformedURLException, URISyntaxException {
    var dmp = mkDMP(1L, "DMP title " + dmpId, "DMP 1 description");
    return dmp;
  }

  @Override
  public <T> T doGet(String accessToken, String path, Class<T> clazz)
      throws URISyntaxException, MalformedURLException {
    return null;
  }

  public void setPdfPath(String pdfPath) {
    this.pdfPath = pdfPath;
  }
}
