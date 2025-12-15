package com.researchspace.webapp.integrations.galaxy;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.integrations.galaxy.service.GalaxyInvocationAndDataCounts;
import com.researchspace.integrations.galaxy.service.GalaxySummaryStatusReport;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.controller.SpringWebClientNotFoundLoggedAsErrorExceptionHandlerVisitor;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequestMapping("/apps/galaxy")
public class GalaxyController extends BaseController {

  private final UserManager userManager;

  @Autowired private com.researchspace.integrations.galaxy.service.GalaxyService galaxyService;
  private SpringWebClientNotFoundLoggedAsErrorExceptionHandlerVisitor exceptionHandlerVisitor =
      new SpringWebClientNotFoundLoggedAsErrorExceptionHandlerVisitor();

  public GalaxyController(UserManager userManager) {
    this.userManager = userManager;
  }

  @Override
  @ExceptionHandler()
  public ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e) {
    return exceptionHandler.handleExceptions(request, response, e, exceptionHandlerVisitor);
  }

  @PostMapping("/setUpDataInGalaxyFor")
  public History setUpDataInGalaxyFor(
      @RequestParam() String targetAlias,
      @RequestParam() long recordId,
      @RequestParam() long fieldId,
      @RequestParam() long[] selectedAttachmentIds,
      HttpServletRequest request)
      throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    String serverAddress = RequestUtil.getAppURL(request);
    History galaxyHistory =
        galaxyService.setUpDataInGalaxyFor(
            user, recordId, fieldId, selectedAttachmentIds, serverAddress, targetAlias);
    return galaxyHistory;
  }

  @GetMapping("/getSummaryGalaxyDataForRSpaceField/{fieldId}")
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getSummaryGalaxyDataForRSpaceField(fieldId, user);
  }

  @GetMapping("/getGalaxyInvocationCountForRSpaceField/{fieldId}")
  public GalaxyInvocationAndDataCounts getGalaxyInvocationCountForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getGalaxyInvocationCountForRSpaceField(fieldId, user);
  }

  @GetMapping("/galaxyDataExists/{fieldId}")
  public Boolean galaxyDataExistsForRSpaceField(@PathVariable long fieldId) {
    return galaxyService.galaxyDataExists(fieldId);
  }
}
