package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RaidGroupAssociationDTO implements Serializable {

  private Long projectGroupId;
  private String rspaceProjectName;
  private RaIDReferenceDTO raid;

  @JsonIgnore private String roCrateId;

  public RaidGroupAssociationDTO(UserRaid userRaid) {
    this.projectGroupId = userRaid.getGroupAssociated().getId();
    this.raid = new RaIDReferenceDTO(userRaid);
    this.rspaceProjectName = userRaid.getGroupAssociated().getDisplayName();
    initRoCrateId();
  }

  public RaidGroupAssociationDTO(
      Long projectGroupId, String rspaceProjectName, RaIDReferenceDTO raid) {
    this.projectGroupId = projectGroupId;
    this.raid = raid;
    this.rspaceProjectName = rspaceProjectName;
    initRoCrateId();
  }

  public String getRoCrateId() {
    if (this.roCrateId == null) {
      initRoCrateId();
    }
    return this.roCrateId;
  }

  private void initRoCrateId() {
    this.roCrateId = "#project-" + this.rspaceProjectName + "-" + this.projectGroupId;
  }
}
