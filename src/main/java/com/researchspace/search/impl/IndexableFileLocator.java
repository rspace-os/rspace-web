package com.researchspace.search.impl;

import com.researchspace.core.util.MediaUtils;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** */
@Slf4j
class IndexableFileLocator {
  private final File topLevelFolder;
  private Set<String> fileStoreSectionsToInclude;

  public IndexableFileLocator(File topLevelFolder) {
    this.topLevelFolder = topLevelFolder;
    initWhiteListFileStoreSections();
  }

  private void initWhiteListFileStoreSections() {
    // 'pdf' and 'doc' are where PDF/doc converted docs go.
    this.fileStoreSectionsToInclude =
        Set.of(
                MediaUtils.DOCUMENT_MEDIA_FLDER_NAME,
                MediaUtils.MISC_MEDIA_FLDER_NAME,
                MediaUtils.DMP_MEDIA_FLDER_NAME,
                "pdf",
                "doc")
            .stream()
            .map(s -> topLevelFolder.getAbsolutePath() + File.separator + s)
            .collect(Collectors.toSet());
  }

  void doExtractFiles(File currFolder, List<File> fileList) {
    boolean match =
        fileStoreSectionsToInclude.stream()
            .anyMatch(p -> currFolder.getAbsolutePath().startsWith(p));
    if (!match && !currFolder.equals(topLevelFolder)) {
      log.info("Excluding filestore section [{}] and its subfolders from indexing", currFolder);
      return;
    }
    log.debug("Current file count is {}", fileList.size());
    log.info("Looking at folder {}", currFolder.getAbsolutePath());
    File[] files = currFolder.listFiles();
    if (files == null) {
      throw new IllegalArgumentException(
          String.format("%s is  not a folder", currFolder.getAbsolutePath()));
    }
    log.debug("Adding {} files for indexing!", files.length);
    for (File fx : files) {
      if (!fx.exists()) {
        continue;
      }
      if (fx.isDirectory()) {
        doExtractFiles(fx, fileList);
      } else {
        fileList.add(fx);
      }
    }
  }
}
