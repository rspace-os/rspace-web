package com.researchspace.service.archive.export;

import com.researchspace.model.EcatDocumentFile;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@EqualsAndHashCode
@Data
public class ExportEcatDocumentResult {

  private EcatDocumentFile ecatDocumentFile;
  private Set<String> igsnInventoryLinkedItems;
}
