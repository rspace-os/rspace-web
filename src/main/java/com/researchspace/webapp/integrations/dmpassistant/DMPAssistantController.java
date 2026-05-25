package com.researchspace.webapp.integrations.dmpassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import java.security.Principal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Bearer-token API proxy controller for DMP Assistant (Portage Network roadmap fork). Each method
 * resolves the caller's User from the Principal; the {@link DMPAssistantProvider} loads the user's
 * personal access token from {@code UserConnection} and forwards it as a Bearer credential against
 * {@code dmpassistant.base.url}.
 */
@Slf4j
@Controller
@RequestMapping("/apps/dmpassistant")
public class DMPAssistantController extends BaseController {

  static final String APP_NAME = "DMPASSISTANT";

  @Autowired private UserManager userManager;
  @Autowired private MediaManager mediaManager;
  @Autowired private DMPManager dmpManager;
  @Autowired private DMPAssistantProvider dmpAssistantProvider;

  @GetMapping("/me")
  @ResponseBody
  public AjaxReturnObject<JsonNode> me(Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.me(user));
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listPlans(
      @RequestParam(name = "page", defaultValue = "1") String page,
      @RequestParam(name = "per_page", defaultValue = "20") String perPage,
      @RequestParam(name = "complete", required = false) Boolean complete,
      Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.listPlans(page, perPage, complete, user));
  }

  @GetMapping("/plans/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> getPlanById(
      @PathVariable("id") String id,
      @RequestParam(name = "complete", required = false) Boolean complete,
      Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.getPlanById(id, complete, user));
  }

  @PostMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> createPlan(@RequestBody JsonNode body, Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.createPlan(body, user));
  }

  @PutMapping("/plans/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> editPlanAnswers(
      @PathVariable("id") String id, @RequestBody JsonNode body, Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.editPlanAnswers(id, body, user));
  }

  @GetMapping("/templates")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listTemplates(Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.listTemplates(user));
  }

  @GetMapping("/templates/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> getTemplateById(
      @PathVariable("id") String id, Principal principal) {
    return proxy(principal, user -> dmpAssistantProvider.getTemplateById(id, user));
  }

  /**
   * Fetches the named plan from DMP Assistant and saves it as a new EcatDocumentFile in the
   * Gallery, then registers a DMPUser row so subsequent exports can attach the DMP.
   */
  @PostMapping("/importPlan")
  @ResponseBody
  public AjaxReturnObject<JsonNode> importPlan(
      @RequestParam("id") String id, @RequestParam("filename") String filename, Principal principal)
      throws java.io.IOException {
    try {
      User user = userManager.getUserByUsername(principal.getName());
      JsonNode plan = dmpAssistantProvider.getPlanById(id, true, user);
      User authenticated = userManager.getAuthenticatedUserInSession();
      ObjectMapper mapper = new ObjectMapper();
      byte[] bytes = mapper.writeValueAsBytes(plan);
      EcatDocumentFile file =
          mediaManager.saveNewDMP(
              filename, new java.io.ByteArrayInputStream(bytes), authenticated, null);
      JsonNode dmpNode = plan.has("dmp") ? plan.get("dmp") : plan;
      String title = dmpNode.path("title").asText(filename);
      Optional<DMPUser> existing = dmpManager.findByDmpId(id, authenticated);
      DMPUser dmpUser =
          existing.orElseGet(
              () ->
                  new DMPUser(
                      authenticated,
                      new DmpDto(
                          id,
                          title,
                          DMPSource.DMP_ASSISTANT,
                          dmpNode.path("dmp_id").path("identifier").asText(null),
                          dmpNode.path("dmp_id").path("identifier").asText(null))));
      if (file != null) {
        dmpUser.setDmpDownloadFile(file);
      }
      dmpManager.save(dmpUser);
      return new AjaxReturnObject<>(plan, null);
    } catch (HttpStatusCodeException e) {
      log.warn("DMP Assistant import failed: {}", e.getMessage());
      return new AjaxReturnObject<>(null, ErrorList.of(e.getMessage()));
    }
  }

  private interface ProviderCall {
    JsonNode call(User user) throws Exception;
  }

  private AjaxReturnObject<JsonNode> proxy(Principal principal, ProviderCall call) {
    try {
      User user = userManager.getUserByUsername(principal.getName());
      return new AjaxReturnObject<>(call.call(user), null);
    } catch (HttpStatusCodeException e) {
      log.warn("DMP Assistant request failed: {}", e.getMessage());
      return new AjaxReturnObject<>(null, ErrorList.of(e.getMessage()));
    } catch (Exception e) {
      log.warn("Error connecting to DMP Assistant", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Error connecting to DMP Assistant."));
    }
  }
}
