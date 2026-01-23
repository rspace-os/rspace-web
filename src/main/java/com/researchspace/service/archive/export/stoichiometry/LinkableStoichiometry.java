package com.researchspace.service.archive.export.stoichiometry;

import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;

public class LinkableStoichiometry implements IFieldLinkableElement {

  private final StoichiometryDTO stoichiometry;

  public LinkableStoichiometry(StoichiometryDTO stoichiometry) {
    this.stoichiometry = stoichiometry;
  }

  @Override
  public Long getId() {
    return stoichiometry.getId();
  }

  @Override
  public GlobalIdentifier getOid() {
    // FIXME
    return new GlobalIdentifier(GlobalIdPrefix.SD, stoichiometry.getParentReactionId());
  }

  public StoichiometryDTO getStoichiometry() {
    return stoichiometry;
  }
}
