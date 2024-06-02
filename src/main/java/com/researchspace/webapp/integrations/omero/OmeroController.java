package com.researchspace.webapp.integrations.omero;

import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import com.researchspace.integrations.omero.service.OmeroService;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserManager;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class OmeroAuthException extends RuntimeException {

    public OmeroAuthException(String message) {
      super(message);
    }
  }

  @Autowired
  @Qualifier("userNameToUserConnection")
  private Map<String, UserConnection> userUserConnectionMap;

  public OmeroController(UserManager userManager, OmeroService omeroService) {
    this.userManager = userManager;
    this.omeroExceptionHandler = new OmeroExceptionHandler();
    this.omeroService = omeroService;
  }

  @ExceptionHandler()
  public ResponseEntity<String> handleExceptions(Exception e) {
    return omeroExceptionHandler.handle(e);
  }

  @GetMapping("/projects")
  public List<? extends OmeroRSpaceView> getProjects(
      @RequestParam(required = false) String dataType) {
    User user = userManager.getAuthenticatedUserInSession();
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
    ImageRSpaceView image = omeroService.getImage(cred, imageid, datasetid, fetchLarge);
    long end = System.currentTimeMillis();
    log.debug("get full single image took: " + (end - start));
    return image;
  }

  @GetMapping("/annotations/{id}")
  public List<String> getAnnotations(@PathVariable long id, @RequestParam String type) {
    User user = userManager.getAuthenticatedUserInSession();
    long start = System.currentTimeMillis();
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
    UserConnection uc = userUserConnectionMap.get("omero_" + user.getUsername());
    if (uc == null) {
      throw new OmeroAuthException(
          "Omero authentication expired, please connect to Omero on the Apps page");
    }
    String cred =
        uc.getAccessToken(); // we save omero credentials as a delimited string in the access
    // token field of UserConnection table
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
