package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
public class StoreFileContentsHashForInventoryFileProperties_rsdev292 extends AbstractCustomLiquibaseUpdater {

  private int fileCounter;
  private FileStore fileStore;

  @Override
  protected void addBeans() {
    fileStore = context.getBean("compositeFileStore", FileStore.class);
  }

  @Override
  public String getConfirmationMessage() {
    return String.format(
        "Populated contentsHash column in the FileProperty table for %d entities.", fileCounter);
  }

  @Override
  protected void doExecute(Database database) {
    List<FileProperty> fileProperties = getAllInventoryFileProperties();
    for (FileProperty fileProperty : fileProperties) {
      if (StringUtils.isBlank(fileProperty.getContentsHash())) {
        try {
          Optional<FileInputStream> fileContents = fileStore.retrieve(fileProperty);
          if (fileContents.isPresent()) {
            String contentsHash =
                CryptoUtils.hashWithSha256inHex(Arrays.toString(fileContents.get().readAllBytes()));
            fileProperty.setContentsHash(contentsHash);
            fileCounter++;
          }
        } catch (IOException e) {
          log.warn(
              "Unable to perform migration for {}. Cause: {}",
              fileProperty.getFileName(),
              e.getMessage());
        }
      }
    }
  }

  private List<FileProperty> getAllInventoryFileProperties() {
    List<FileProperty> fileProperties = new ArrayList<>();
    fileProperties.addAll(getContainerFileProperties());
    fileProperties.addAll(getSampleFileProperties());
    fileProperties.addAll(getSubsampleFileProperties());
    return fileProperties;
  }

  private List<FileProperty> getContainerFileProperties(){
    return sessionFactory
        .getCurrentSession()
        .createQuery("select imageFileProperty, thumbnailFileProperty, "
            + "locationsImageFileProperty from Container", FileProperty.class)
        .list();
  }

  private List<FileProperty> getSampleFileProperties(){
    return sessionFactory
        .getCurrentSession()
        .createQuery("select imageFileProperty, thumbnailFileProperty from Sample",
            FileProperty.class)
        .list();
  }

  private List<FileProperty> getSubsampleFileProperties(){
    return sessionFactory
        .getCurrentSession()
        .createQuery("select imageFileProperty, thumbnailFileProperty from SubSample",
            FileProperty.class)
        .list();
  }
}
