package com.researchspace.service.inventory.csvexport;

import com.researchspace.core.util.ZipUtils;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.service.archive.export.ArchiveNamingStrategy;
import java.io.File;
import java.io.IOException;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Setter
@Component
public class InventoryExportFileCreator {

  private @Autowired ArchiveNamingStrategy archiveNamingStrategy;
  private @Autowired IExportUtils exportUtils;

  private @Autowired IPropertyHolder properties;

  private String exportFolderLocation;

  public String getExportFolderLocation() {
    if (exportFolderLocation == null) {
      exportFolderLocation = properties.getExportFolderLocation();
    }
    return exportFolderLocation;
  }

  public File saveCsvContentIntoExportFolder(String content, String fileName, File exportFolder)
      throws IOException {
    if (fileName == null) {
      fileName = archiveNamingStrategy.generateArchiveName("CSV", "INVENTORY") + ".csv";
    }
    if (exportFolder == null) {
      exportFolder = new File(getExportFolderLocation());
    }
    File csvFile = new File(exportFolder, fileName);
    FileUtils.write(csvFile, content, "UTF-8");
    return csvFile;
  }

  public File createCsvArchiveAssemblyFolder() throws IOException {
    String folderName = archiveNamingStrategy.generateArchiveName("CSV", "INVENTORY");
    File exportFolder = new File(getExportFolderLocation());
    File archiveAssemblyFolder = new File(exportFolder, folderName);
    exportUtils.createFolder(archiveAssemblyFolder);
    return archiveAssemblyFolder;
  }

  public File zipAssemblyFolder(File assemblyFolder) throws IOException {
    String zipFileName = assemblyFolder.getName() + ".zip";
    File zipFile = new File(getExportFolderLocation(), zipFileName);
    ZipUtils.createZip(zipFile, assemblyFolder);
    return zipFile;
  }
}
