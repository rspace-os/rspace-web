package com.researchspace.webapp.integrations.argos;

import com.researchspace.model.dmps.argos.ArgosDMPListing;
import com.researchspace.model.dmps.argos.DataTableData;
import com.researchspace.model.field.ErrorList;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
@RequestMapping("/apps/argos/")
public class ArgosController extends BaseController {

  @Autowired private ArgosDMPProvider client;

  @PostMapping("/importPlan/{id}")
  @ResponseBody
  public AjaxReturnObject<Boolean> importDmp(@PathVariable("id") String id) {
    // Argos plan ids are UUIDs. Parsing here keeps the raw path variable out of the outbound
    // URL and gives one stored id per plan, whatever spelling the request used.
    UUID planId;
    try {
      planId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      log.warn("Rejected DMP import with an id that is not a UUID");
      return new AjaxReturnObject<>(null, ErrorList.of(getText("apps.dmp.errors.import")));
    }
    try {
      return new AjaxReturnObject<>(client.importDmp(planId), null);
    } catch (RestClientException e) {
      logUpstreamFailure("importing DMP", e);
      return new AjaxReturnObject<>(null, ErrorList.of(getText("apps.dmp.errors.import")));
    } catch (IOException e) {
      log.error("Failure on importing DMP", e);
      return new AjaxReturnObject<>(null, ErrorList.of(getText("apps.dmp.errors.import")));
    }
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<DataTableData<ArgosDMPListing>> listDMPs(
      @RequestParam(name = "pageSize", required = true) Integer pageSize,
      @RequestParam(name = "page", required = true) Integer page,
      @RequestParam(name = "like", required = false) String like) {
    try {
      return new AjaxReturnObject<>(client.listPlans(pageSize, page, like), null);
    } catch (RestClientException e) {
      logUpstreamFailure("listing DMPs", e);
      return new AjaxReturnObject<>(null, ErrorList.of(getText("apps.dmp.errors.list")));
    }
  }

  // Logs the upstream status only: Argos response bodies can hold plan content.
  private void logUpstreamFailure(String action, RestClientException e) {
    if (e instanceof RestClientResponseException responseException) {
      log.warn("Argos failure on {}: HTTP {}", action, responseException.getStatusCode().value());
    } else {
      log.warn("Argos failure on {}: {}", action, e.getClass().getSimpleName());
    }
  }
}
