package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.PidinstLookupApi;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.PidinstLookupManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class PidinstLookupApiController extends BaseApiInventoryController
    implements PidinstLookupApi {

  @Autowired private PidinstLookupManager pidinstLookupMgr;
  @Autowired private ApiAvailabilityHandler apiHandler;

  @Override
  public ApiPidinstSearchResult search(
      @RequestParam("query") String query, @RequestAttribute(name = "user") User user) {
    apiHandler.assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.PIDINST);
    // no length or blank check here: the manager refuses a short query with a localized 422, so a
    // direct caller is held to the same rule
    return pidinstLookupMgr.search(query, user);
  }
}
