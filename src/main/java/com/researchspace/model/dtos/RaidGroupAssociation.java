package com.researchspace.model.dtos;

import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RaidGroupAssociation implements Serializable {

  private Long projectGroupId;
  private RaIDReferenceDTO raid;
}
