package com.researchspace.webapp.integrations.dmptool;

import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.views.ServiceOperationResult;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

/** API Client wrapper for making calls to DMP API */
public interface DMPToolDMPProvider {
  /**
   * Attaches a DOI (perhaps obtained from depositing to a repository) to the DMP
   *
   * @param dmpId
   * @param doiIdentifier
   * @param accessToken
   * @return
   */
  ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, String accessToken);

  /**
   * Attaches a DOI (perhaps obtained from depositing to a repository) to the DMP
   *
   * @param dmpId
   * @param doiIdentifier
   * @param user Assumes this user has performed OAUth flow and can retrieve an accessToken from
   *     UserConnection table
   * @return
   */
  ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, User user);

  /**
   * Downloads a PDF of the DMP, creating a new DMP entry in RSpace if one does not already exist.
   *
   * @param id the DMP ID
   * @param title The title of the DMP
   * @param accessToken
   * @return
   * @throws URISyntaxException
   * @throws IOException
   */
  DMPUser doPdfDownload(DMPToolDMP dmp, String title, String accessToken)
      throws URISyntaxException, IOException;

  /**
   * Downloads a PDF of the DMP, creating a new DMP entry in RSpace if one does not already exist.
   *
   * @param id the DMP ID
   * @param title The title of the DMP
   * @param user User. Assumes this user has performed OAUth flow and can retrieve an accessToken
   *     from UserConnection table
   * @return
   * @throws URISyntaxException
   * @throws IOException
   */
  ServiceOperationResult<DMPUser> doPdfDownload(DMPToolDMP dmp, String title, User user)
      throws URISyntaxException, IOException;

  /**
   * Retrieves 1 page of DMPs of given scope
   *
   * @param scope
   * @param accessToken
   * @return
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  DMPList listPlans(DMPPlanScope scope, String accessToken)
      throws MalformedURLException, URISyntaxException;

  /**
   * Retrieves 1 page of DMPs of given scope
   *
   * @param scope
   * @param user User. Assumes this user has performed OAUth flow and can retrieve an accessToken
   *     from UserConnection table
   * @return
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  ServiceOperationResult<DMPList> listPlans(DMPPlanScope scope, User user)
      throws MalformedURLException, URISyntaxException;

  /**
   * Gets details of an individual plan by its ID
   *
   * @param dmpId
   * @param user User. Assumes this user has performed OAUth flow and can retrieve an accessToken
   *     from UserConnection table
   * @return
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  ServiceOperationResult<DMPToolDMP> getPlanById(String dmpId, User user)
      throws MalformedURLException, URISyntaxException;

  /**
   * Gets details of an individual plan by its ID
   *
   * @param dmpId
   * @param accessToken
   * @return
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  DMPToolDMP getPlanById(String dmpId, String accessToken)
      throws MalformedURLException, URISyntaxException;

  /**
   * Generic RestTemplate method to call other API endpoints
   *
   * @param accessToken
   * @param path The endpoint path
   * @param clazz
   * @param <T> The class to convert results to, can be String.
   * @return
   * @throws URISyntaxException
   * @throws MalformedURLException
   */
  public <T> T doGet(String accessToken, String path, Class<T> clazz)
      throws URISyntaxException, MalformedURLException;
}
