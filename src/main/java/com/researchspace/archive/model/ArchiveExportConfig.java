package com.researchspace.archive.model;

import com.researchspace.archive.ArchivalLinkResolver;
import com.researchspace.archive.ArchivalMeta;
import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.repository.spi.IRepository;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Holds user-supplied export configuration and export results. <br>
 * Created as request-scoped bean in BaseConfig, populated via request parameters to override
 * defaults
 */
@Data
public class ArchiveExportConfig implements IArchiveExportConfig {

  private static final int MAX_DESC_SIZE = 500;

  // default not null.
  private IRepository repository = IRepository.NULL_OPS;

  private boolean hasAllVersion = false;

  private boolean isDeposit = false;

  private boolean isELNArchive = false;

  private boolean includeTemplates = true;

  private String description = "";

  private ExportScope exportScope;

  private ProgressMonitor progressMonitor = ProgressMonitor.NULL_MONITOR;

  public void setRepository(IRepository repository) {
    if (repository == null) {
      throw new IllegalArgumentException("repository cannot be null");
    }
    this.repository = repository;
  }

  /**
   * Maximum size of 500 chars; any longer are truncated.
   *
   * @param description
   */
  public void setDescription(String description) {
    this.description = StringUtils.abbreviate(description, MAX_DESC_SIZE);
  }

  private String archiveType = XML;

  /** The default depth of links to incorporate in the archive.s */
  public static final int DEFAULT_MAX_LINK_LEVEL = 1;

  /** HTML export type */
  public static final String HTML = "html";

  /** XML export type */
  public static final String XML = "xml";

  /**
   * Set the max depth of links to follow
   *
   * @param maxLinkLevel
   */
  private int maxLinkLevel = DEFAULT_MAX_LINK_LEVEL;

  private File topLevelExportFolder;

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveConfig#getTopLevelExportFolder()
   */
  public File getTopLevelExportFolder() {
    return topLevelExportFolder;
  }

  public void setTopLevelExportFolder(File topLevelExportFolder) throws IOException {
    if (!topLevelExportFolder.exists() || !topLevelExportFolder.isDirectory()) {
      FileUtils.forceMkdir(topLevelExportFolder);
    }
    this.topLevelExportFolder = topLevelExportFolder;
  }

  // Transient section for export/repeat/result
  private ArchivalMeta archivalMeta;
  private ArchivalLinkResolver resolver;
  private User exporter;

  public void setHasAllVersion(boolean hasAllVersion) {
    this.hasAllVersion = hasAllVersion;
  }

  public ArchiveExportConfig() {
    archivalMeta = new ArchivalMeta();
    resolver = new ArchivalLinkResolver();
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveConfig#isAllVersion()
   */
  public boolean isAllVersion() {
    return hasAllVersion;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.model.archive.IArchiveConfig#isArchive()
   */
  public boolean isArchive() {
    return XML.equals(archiveType);
  }

  /**
   * Given an export file name, returns the name with the appropriate suffix for the content type
   * (HTML or XML)
   *
   * @param documentFileName
   * @return
   */
  public String generateDocumentExportFileName(String documentFileName) {
    if (isArchive()) {
      return documentFileName + ".xml";
    } else {
      return documentFileName + ".html";
    }
  }

  private GlobalIdentifier userOrGroupId;

  private boolean includeNfsLinks = false;
  private long maxNfsFileSize = 0;
  private Set<String> excludedNfsFileExtensions = new HashSet<>();

  private Map<Long, NfsClient> availableNfsClients;

  /** Facade method to configure for user export */
  public ArchiveExportConfig configureUserExport(User toExport) {
    setExportScope(ExportScope.USER);
    setUserOrGroupId(toExport.getOid());
    return this;
  }

  @Override
  public boolean isSelectionScope() {
    return ExportScope.SELECTION.equals(exportScope);
  }

  @Override
  public boolean isUserScope() {
    return ExportScope.USER.equals(exportScope);
  }

  @Override
  public boolean isGroupScope() {
    return ExportScope.GROUP.equals(exportScope);
  }
}
