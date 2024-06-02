package com.researchspace.webapp.integrations.argos;

import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.model.field.ErrorList;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/apps/argos/")
public class ArgosController extends BaseController {

  @Autowired private ArgosDMPProvider client;

  @PostMapping("/importPlan/{id}")
  @ResponseBody
  public AjaxReturnObject<Boolean> importDmp(@PathVariable("id") String id) {
    try {
      return new AjaxReturnObject<>(client.importDmp(id), null);
    } catch (IOException | URISyntaxException e) {
      log.error("Failure on importing DMP", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't import DMP"));
    }
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<DataTableData<ArgosDMPListing>> listDMPs(
      @RequestParam(name = "pageSize", required = true) Integer pageSize,
      @RequestParam(name = "page", required = true) Integer page,
      @RequestParam(name = "like", required = false) String like,
      @RequestParam(name = "grantsLike", required = false) String grantsLike,
      @RequestParam(name = "fundersLike", required = false) String fundersLike,
      @RequestParam(name = "collaboratorsLike", required = false) String collaboratorsLike) {
    try {
      return new AjaxReturnObject<>(
          client.listPlans(pageSize, page, like, grantsLike, fundersLike, collaboratorsLike), null);
    } catch (MalformedURLException | URISyntaxException e) {
      log.error("Failure on listing DMPs", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't list DMPs"));
    }
  }
}
