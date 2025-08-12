package com.researchspace.webapp.integrations.galaxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.integrations.galaxy.service.GalaxyService;
import com.researchspace.integrations.galaxy.service.GalaxySummaryStatusReport;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.controller.SpringWebClientNotFoundLoggedAsErrorExceptionHandlerVisitor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${galaxy.server.config}")
  private String mapString;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GalaxyInvocationAndDataCounts {
    private int invocationCount;
    private int dataCount;
  }

  @Data
  @NoArgsConstructor
  public static class GalaxyAliasToServer {
    private String alias;
    private String url;

    public GalaxyAliasToServer(String alias, String url) {
      this.alias = alias;
      this.url = url;
    }

    @JsonInclude(value = Include.NON_EMPTY)
    private String token;
  }

  private final UserManager userManager;
  @Getter private List<GalaxyAliasToServer> aliasServerPairs;

  @PostConstruct
  private void init() throws JsonProcessingException {
    if (StringUtils.isBlank(mapString)) {
      this.aliasServerPairs = new ArrayList<>();
    } else {
      ObjectMapper objectMapper = new ObjectMapper();
      aliasServerPairs =
          objectMapper.readValue(mapString, new TypeReference<List<GalaxyAliasToServer>>() {});
    }
  }

  @Autowired private GalaxyService galaxyService;
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
            user,
            recordId,
            fieldId,
            selectedAttachmentIds,
            serverAddress,
            aliasServerPairs,
            targetAlias);
    return galaxyHistory;
  }

  @GetMapping("/getSummaryGalaxyDataForRSpaceField/{fieldId}")
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getSummaryGalaxyDataForRSpaceField(fieldId, user, aliasServerPairs);
  }

  @GetMapping("/getGalaxyInvocationCountForRSpaceField/{fieldId}")
  public GalaxyInvocationAndDataCounts getGalaxyInvocationCountForRSpaceField(
      @PathVariable long fieldId) throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    return galaxyService.getGalaxyInvocationCountForRSpaceField(fieldId, user, aliasServerPairs);
  }

  @GetMapping("/galaxyDataExists/{fieldId}")
  public Boolean galaxyDataExistsForRSpaceField(@PathVariable long fieldId) {
    return galaxyService.galaxyDataExists(fieldId);
  }
}
