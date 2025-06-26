package com.researchspace.webapp.integrations.galaxy;

import com.researchspace.files.service.FileStore;
import com.researchspace.galaxy.client.GalaxyClient;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.integrations.galaxy.service.GalaxyService;
import com.researchspace.integrations.galaxy.service.GalaxySummaryStatusReport;
import com.researchspace.model.User;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

  private final GalaxyClient client;
  private final UserManager userManager;
  // TODO Create Galaxy service class
  private @Autowired BaseRecordManager baseRecordManager;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired RecordManager recordManager;
  @Autowired private FieldManager fieldManager;

  @Autowired private GalaxyService galaxyService;

  public GalaxyController(GalaxyClient client, UserManager userManager) {
    this.client = client;
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
      @RequestParam(required = true) long[] selectedAttachmentIds)
      throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    History galaxyHistory =
        galaxyService.setUpDataInGalaxyFor(user, recordId, fieldId, selectedAttachmentIds);
    return galaxyHistory;
  }

  @GetMapping("/getSummaryGalaxyDataForRSpaceField/{fieldId}")
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getSummaryGalaxyDataForRSpaceField(fieldId, user);
  }
}
