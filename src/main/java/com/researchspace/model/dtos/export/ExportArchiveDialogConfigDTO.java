package com.researchspace.model.dtos.export;

import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.NfsExportConfig;
import com.researchspace.model.repository.RepoDepositConfig;
import java.util.HashSet;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
public class ExportArchiveDialogConfigDTO {

  @Data
  @NoArgsConstructor
  public static class ArchiveDialogConfig {
    @NotNull(message = "maxLinkLevel {errors.required.field}")
    Integer maxLinkLevel;

    @NotNull(message = "archiveType {errors.required.field}")
    String archiveType;

    @Size(max = 500, message = "{description} {errors.string.max}")
    @NotNull(message = "description {errors.required.field}")
    String description;

    @NotNull(message = "allVersions {errors.required.field}")
    Boolean allVersions;
  }

  @Valid private ExportSelection exportSelection;
  @Valid private ArchiveDialogConfig exportConfig;
  @Valid private NfsExportConfig nfsConfig;
  @Valid private RepoDepositConfig repositoryConfig;

  public ArchiveExportConfig toArchiveExportConfig() {
    ArchiveExportConfig archiveExportCfg = new ArchiveExportConfig();
    archiveExportCfg.setMaxLinkLevel(exportConfig.getMaxLinkLevel());
    archiveExportCfg.setArchiveType(exportConfig.getArchiveType());
    if (exportConfig.getArchiveType().equals("eln")) {
      archiveExportCfg.setArchiveType("xml");
      archiveExportCfg.setELNArchive(true);
    }
    archiveExportCfg.setDescription(exportConfig.getDescription());
    archiveExportCfg.setHasAllVersion(exportConfig.getAllVersions());
    archiveExportCfg.setExportScope(ExportScope.valueOf(exportSelection.getType().name()));

    if (repositoryConfig != null) {
      archiveExportCfg.setDeposit(repositoryConfig.isDepositToRepository());
    }
    if (nfsConfig != null && nfsConfig.getIncludeNfsFiles()) {
      archiveExportCfg.setIncludeNfsLinks(true);
      if (nfsConfig.getMaxFileSizeInMB() != null) {
        archiveExportCfg.setMaxNfsFileSize(nfsConfig.getMaxFileSizeInMB() * FileUtils.ONE_MB);
      }
      if (!StringUtils.isEmpty(nfsConfig.getExcludedFileExtensions())) {
        Set<String> excludedExts = new HashSet<>();
        for (String ext : nfsConfig.getExcludedFileExtensions().trim().split(",")) {
          // handle user inputs with dots, e.g. '.txt, .pdf'
          String extToAdd = ext.trim().startsWith(".") ? ext.substring(1) : ext;
          excludedExts.add(extToAdd.toLowerCase());
        }
        archiveExportCfg.setExcludedNfsFileExtensions(excludedExts);
      }
    }
    return archiveExportCfg;
  }
}
