package com.researchspace.model.dtos.export;

import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.repository.RepoDepositConfig;
import javax.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class AbstractExportDialog {

  @Valid protected ExportSelection exportSelection;
  @Valid protected RepoDepositConfig repositoryConfig;
  @Valid protected RaidGroupAssociationDTO raidAssociated;
}
