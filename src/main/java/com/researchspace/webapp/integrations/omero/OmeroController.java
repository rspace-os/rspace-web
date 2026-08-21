package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;

import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import com.researchspace.integrations.omero.service.OmeroService;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/apps/omero")
public class OmeroController {
  private final OmeroExceptionHandler omeroExceptionHandler;
  private final UserManager userManager;
  private final OmeroService omeroService;
  private final MessageSourceUtils messages;
  private final UserConnectionManager userConnectionManager;

  /** The user has no stored OMERO connection, either never made or since disconnected. */
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class OmeroNotConnectedException extends RuntimeException {

    public OmeroNotConnectedException(String message) {
      super(message);
    }
  }

  public OmeroController(
      UserManager userManager,
      OmeroService omeroService,
      MessageSourceUtils messages,
      UserConnectionManager userConnectionManager) {
    this.userManager = userManager;
    this.messages = messages;
    this.omeroExceptionHandler = new OmeroExceptionHandler(messages);
    this.omeroService = omeroService;
    this.userConnectionManager = userConnectionManager;
  }

  /**
   * The OMERO username and password are stored as a delimited string in the access token field of
   * the UserConnection table, encrypted at rest by the DAO.
   */
  private String omeroCredentials(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), OMERO_APP_NAME)
        .map(UserConnection::getAccessToken)
        .orElseThrow(
            () ->
                new OmeroNotConnectedException(
                    messages.getMessage("apps.omero.errors.notConnected")));
  }

  @ExceptionHandler()
  public ResponseEntity<String> handleExceptions(Exception e) {
    return omeroExceptionHandler.handle(e);
  }

  @GetMapping("/projects")
  public List<? extends OmeroRSpaceView> getProjects(
      @RequestParam(required = false) String dataType) {
    User user = userManager.getAuthenticatedUserInSession();
    String cred = omeroCredentials(user);
    long start = System.currentTimeMillis();
    List<? extends OmeroRSpaceView> projectsAndScreens =
        omeroService.getProjectsAndScreens(cred, dataType);
    long end = System.currentTimeMillis();
    log.debug("get projects and screens took: " + (end - start));
    for (OmeroRSpaceView p : projectsAndScreens) {
      p.setOmeroConnectionKey(user.getUsername());
    }
    return projectsAndScreens;
  }

  @GetMapping("/datasets/{projectid}")
  public List<DataSetRSpaceView> getDatasetsForProject(@PathVariable long projectid) {
    long start = System.currentTimeMillis();
    User user = userManager.getAuthenticatedUserInSession();
    String cred = omeroCredentials(user);
    List<DataSetRSpaceView> datasets = omeroService.getDataSets(cred, projectid);
    for (DataSetRSpaceView dataset : datasets) {
      dataset.setOmeroConnectionKey(user.getUsername());
    }
    long end = System.currentTimeMillis();
    log.debug("get datasets for project took: " + (end - start));
    return datasets;
  }

  @GetMapping("/plates/{screenid}")
  public List<PlateRSpaceView> getPlatesForScreen(@PathVariable long screenid) {
    long start = System.currentTimeMillis();
    User user = userManager.getAuthenticatedUserInSession();
    String cred = omeroCredentials(user);
    List<PlateRSpaceView> plates = omeroService.getPlates(cred, screenid);
    for (PlateRSpaceView plate : plates) {
      plate.setOmeroConnectionKey(user.getUsername());
    }
    long end = System.currentTimeMillis();
    log.debug("get plates for screen took: " + (end - start));
    return plates;
  }

  @GetMapping("/images/{id}")
  public List<ImageRSpaceView> getImages(@PathVariable long id, @RequestParam boolean fetchLarge) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    String cred = omeroCredentials(user);
    List<ImageRSpaceView> images = omeroService.getImages(cred, id, fetchLarge);
    for (ImageRSpaceView img : images) {
      img.setOmeroConnectionKey(user.getUsername());
    }
    long end = System.currentTimeMillis();
    log.debug("get images for dataset took: " + (end - start));
    return images;
  }

  @GetMapping("/image/{datasetid}/{imageid}")
  public ImageRSpaceView getImage(
      @PathVariable long datasetid, @PathVariable long imageid, @RequestParam boolean fetchLarge) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    String cred = omeroCredentials(user);
    ImageRSpaceView image = omeroService.getImage(cred, imageid, datasetid, fetchLarge);
    long end = System.currentTimeMillis();
    log.debug("get full single image took: " + (end - start));
    return image;
  }

  @GetMapping("/annotations/{id}")
  public List<String> getAnnotations(@PathVariable long id, @RequestParam String type) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    String cred = omeroCredentials(user);
    List<String> annotations = omeroService.getAnnotations(cred, id, type);
    long end = System.currentTimeMillis();
    log.debug("get annotations for " + type + " took: " + (end - start));
    return annotations;
  }

  @GetMapping("/wells/{parentid}/{id}")
  public List<WellRSpaceView> getWells(
      @PathVariable long parentid,
      @PathVariable long id,
      @RequestParam boolean fetchLarge,
      @RequestParam int wellIndex) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    String cred = omeroCredentials(user);
    List<WellRSpaceView> wells = omeroService.getWells(cred, parentid, id, fetchLarge, wellIndex);
    for (WellRSpaceView well : wells) {
      well.setOmeroConnectionKey(user.getUsername());
    }
    long end = System.currentTimeMillis();
    log.debug("get wells for plate took: " + (end - start));
    return wells;
  }

  @GetMapping("/plateAcquisitions/{plateID}")
  public List<PlateAcquisitionRSpaceView> getPlateAcquisitions(@PathVariable long plateID) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    String cred = omeroCredentials(user);
    List<PlateAcquisitionRSpaceView> plateAcquisitions =
        omeroService.getPlateAcquisitions(cred, plateID);
    for (PlateAcquisitionRSpaceView pa : plateAcquisitions) {
      pa.setOmeroConnectionKey(user.getUsername());
    }
    long end = System.currentTimeMillis();
    log.debug("get plate acquisitions for plate took: " + (end - start));
    return plateAcquisitions;
  }
}
