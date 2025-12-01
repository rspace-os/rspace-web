package com.researchspace.api.v1.controller.stoichiometry;

import com.researchspace.api.v1.StoichiometryInventoryLinkApi;
import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryLinkQuantityUpdateRequest;
import com.researchspace.model.User;
import com.researchspace.service.StoichiometryInventoryLinkManager;
import org.springframework.beans.factory.annotation.Autowired;

@ApiController
public class StoichiometryInventoryLinkApiController extends BaseApiController
    implements StoichiometryInventoryLinkApi {

  private final StoichiometryInventoryLinkManager linkService;

  @Autowired
  public StoichiometryInventoryLinkApiController(StoichiometryInventoryLinkManager linkService) {
    this.linkService = linkService;
  }

  public StoichiometryInventoryLinkDTO create(
      StoichiometryInventoryLinkRequest request, User user) {
    return linkService.createLink(request, user);
  }

  public StoichiometryInventoryLinkDTO get(long id, User user) {
    return linkService.getById(id, user);
  }

  public StoichiometryInventoryLinkDTO updateQuantity(
      StoichiometryLinkQuantityUpdateRequest request, User user) {
    return linkService.updateQuantity(
        request.getStoichiometryLinkId(), request.getNewQuantity(), user);
  }

  public void delete(long id, User user) {
    linkService.deleteLink(id, user);
  }
}
