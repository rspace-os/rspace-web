package com.researchspace.webapp.integrations.dcd;

import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.views.ServiceOperationResult;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import org.springframework.stereotype.Service;

@Service
/** API Client wrapper for making calls to DCD API */
public class DigitalCommonsDataManagerImpl implements DigitalCommonsDataManager {

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, String accessToken) {
    return null;
  }

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, User user) {
    return null;
  }

  @Override
  public DMPUser doJsonDownload(DMPToolDMP dmp, String title, String accessToken)
      throws URISyntaxException, IOException {
    return null;
  }

  @Override
  public ServiceOperationResult<DMPUser> doJsonDownload(DMPToolDMP dmp, String title, User user)
      throws URISyntaxException, IOException {
    return null;
  }

  @Override
  public DMPList listPlans(DMPPlanScope scope, String accessToken)
      throws MalformedURLException, URISyntaxException {
    return null;
  }

  @Override
  public ServiceOperationResult<DMPList> listDatasets(DMPPlanScope scope, User user)
      throws MalformedURLException, URISyntaxException {
    return null;
  }

  //
  //  @Override
  //  public DcdModel getPlanById(String dmpId, User accessToken)
  //      throws MalformedURLException, URISyntaxException {
  //    return null;
  //  }

  @Override
  public <T> T doGet(String accessToken, String path, Class<T> clazz)
      throws URISyntaxException, MalformedURLException {
    return null;
  }

  @Override
  public ServiceOperationResult<DMPList> listPlans(DMPPlanScope scope, User user) {
    return null;
  }
}
