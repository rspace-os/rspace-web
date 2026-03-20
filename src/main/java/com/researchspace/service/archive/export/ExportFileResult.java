package com.researchspace.service.archive.export;

import java.io.File;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@EqualsAndHashCode
@Data
public class ExportFileResult {

  private File file;
  private Set<String> igsnInventoryLinkedItems;
}
