package com.researchspace.service.archive.export;

import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Used in archiving process to copy static resources such as icons <em>from</em> a classpath
 * resource <em>to</em> the archive resources folder.
 */
@Component("resourceToArchiveCopier")
@Slf4j
public class ResourceToArchiveCopier {
  @Autowired private ResourceLoader resourceLocator;

  public Optional<String> copyFromClassPathResourceToArchiveResources(
      String url, File exportFolder) {
    String resourceFileName = FilenameUtils.getName(url);
    Resource is = resourceLocator.getResource(url);
    if (!is.exists()) {
      log.warn("resource {} does not exist; cannot add to archive.", url);
      return Optional.empty();
    }
    File target =
        new File(
            exportFolder.getAbsolutePath()
                + File.separator
                + ExportImport.RESOURCES
                + File.separator
                + resourceFileName);

    try (InputStream istream = is.getInputStream();
        FileOutputStream fos = new FileOutputStream(target)) {
      IOUtils.copy(istream, fos);
    } catch (IOException e) {
      log.warn(e.getMessage());
    }
    return Optional.of(resourceFileName);
  }
}
