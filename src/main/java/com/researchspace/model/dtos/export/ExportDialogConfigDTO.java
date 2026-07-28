package com.researchspace.model.dtos.export;

import com.researchspace.export.pdf.ExportToFileConfig;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportDialogConfigDTO extends AbstractExportDialog {

  @Valid private ExportToFileConfig exportConfig;
}
