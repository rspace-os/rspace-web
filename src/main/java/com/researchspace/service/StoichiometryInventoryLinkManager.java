package com.researchspace.service;

import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.model.User;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import java.util.List;

public interface StoichiometryInventoryLinkManager {

  StoichiometryInventoryLink createLink(
      Long stoichiometryMoleculeId, StoichiometryInventoryLinkRequest req, User user);

  StockDeductionResult deductStock(long stoichiometryId, List<Long> linkIds, User user);
}
