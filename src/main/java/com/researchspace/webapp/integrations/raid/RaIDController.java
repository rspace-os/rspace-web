package com.researchspace.webapp.integrations.raid;

import com.researchspace.model.field.ErrorList;
import com.researchspace.raid.model.RaID;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;

@Controller
@RequestMapping("/apps/raid")
public class RaIDController extends BaseOAuth2Controller {

  @Autowired private RaIDServiceClientAdapter raidServiceClientAdapter;

  public RaIDController() {}

  @GetMapping()
  @ResponseBody
  public AjaxReturnObject<Map<String, Set<RaID>>> getRaidListByUser(Principal principal) {
    try {
      Map<String, RaIDServerConfigurationDTO> serverByAlias =
          raidServiceClientAdapter.getServerMapByAlias();
      Map<String, Set<RaID>> raidsByServerAlias = new HashMap<>();
      Set<RaID> raidList = null;
      // for each server get the list of RaID
      for (String currentServerAlias : serverByAlias.keySet()) {
        raidList = raidServiceClientAdapter.getRaIDList(principal.getName(), currentServerAlias);
        if (raidList != null && raidList.isEmpty()) {
          raidsByServerAlias.put(currentServerAlias, raidList);
        }
      }
      return new AjaxReturnObject<>(raidsByServerAlias, null);
    } catch (HttpClientErrorException e) {
      log.warn("error connecting to RaID", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Error connecting to RaID."));
    }
  }

  // TODO: RSDEV-849 - Create all the other end points (but Auth2.0) for managing RaID
}
