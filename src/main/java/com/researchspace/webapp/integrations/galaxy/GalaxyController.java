package com.researchspace.webapp.integrations.galaxy;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.integrations.galaxy.service.GalaxyService;
import com.researchspace.integrations.galaxy.service.GalaxySummaryStatusReport;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apps/galaxy")
public class GalaxyController {

  private final UserManager userManager;

  @Autowired private GalaxyService galaxyService;

  public GalaxyController(UserManager userManager) {
    this.userManager = userManager;
  }

  @ExceptionHandler()
  public ResponseEntity<String> handleExceptions(Exception e) {
    // TODO handle exceptions
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @PostMapping("/setUpDataInGalaxyFor")
  public History setUpDataInGalaxyFor(
      Principal principal,
      @RequestParam(required = true) long recordId,
      @RequestParam(required = true) long fieldId,
      @RequestParam(required = true) long[] selectedAttachmentIds,
      HttpServletRequest request)
      throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    String serverAddress = RequestUtil.getAppURL(request);
    History galaxyHistory =
        galaxyService.setUpDataInGalaxyFor(
            user, recordId, fieldId, selectedAttachmentIds, serverAddress);
    return galaxyHistory;
  }

  @GetMapping("/getSummaryGalaxyDataForRSpaceField/{fieldId}")
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getSummaryGalaxyDataForRSpaceField(fieldId, user);
  }

  @GetMapping("/galaxyDataExists/{fieldId}")
  public Boolean galaxyDataExistsForRSpaceField(@PathVariable long fieldId) {
    return galaxyService.galaxyDataExists(fieldId);
  }
}
