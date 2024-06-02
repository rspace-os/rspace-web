package com.researchspace.model.dtos.export;

import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.repository.RepoDepositConfig;
import javax.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportDialogConfigDTO {

  @Valid private ExportSelection exportSelection;
  @Valid private ExportToFileConfig exportConfig;
  @Valid private RepoDepositConfig repositoryConfig;
}
