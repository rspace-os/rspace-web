package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.customliquibaseupdates.models.ContainerFileProperties;
import com.researchspace.dao.customliquibaseupdates.models.SampleOrSubsampleFileProperty;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.persistence.TypedQuery;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
public class StoreFileContentsHashForInventoryFileProperties_rsdev292
    extends AbstractCustomLiquibaseUpdater {

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

  private List<FileProperty> getContainerFileProperties() {
    TypedQuery<ContainerFileProperties> q =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new"
                    + " com.researchspace.dao.customliquibaseupdates.models.ContainerFileProperties(c.imageFileProperty,"
                    + " c.thumbnailFileProperty, c.locationsImageFileProperty) from Container c",
                ContainerFileProperties.class);
    List<ContainerFileProperties> containerFileProperties = q.getResultList();

    List<FileProperty> fileProperties = new ArrayList<>();
    for (ContainerFileProperties containerFileProperty : containerFileProperties) {
      fileProperties.add(containerFileProperty.getImageFileProperty());
      fileProperties.add(containerFileProperty.getThumbnailFileProperty());
      fileProperties.add(containerFileProperty.getLocationsImageFileProperty());
    }
    return fileProperties;
  }

  private List<FileProperty> getSampleFileProperties() {
    TypedQuery<SampleOrSubsampleFileProperty> q =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new"
                    + " com.researchspace.dao.customliquibaseupdates.models.SampleOrSubsampleFileProperty(s.imageFileProperty,"
                    + " s.thumbnailFileProperty) from Sample s",
                SampleOrSubsampleFileProperty.class);
    List<SampleOrSubsampleFileProperty> sampleFileProperties = q.getResultList();

    return extractSampleOrSubsampleFileProperties(sampleFileProperties);
  }

  private List<FileProperty> getSubsampleFileProperties() {
    TypedQuery<SampleOrSubsampleFileProperty> q =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new"
                    + " com.researchspace.dao.customliquibaseupdates.models.SampleOrSubsampleFileProperty(s.imageFileProperty,"
                    + " s.thumbnailFileProperty) from SubSample s",
                SampleOrSubsampleFileProperty.class);
    List<SampleOrSubsampleFileProperty> sampleFileProperties = q.getResultList();

    return extractSampleOrSubsampleFileProperties(sampleFileProperties);
  }

  private static List<FileProperty> extractSampleOrSubsampleFileProperties(
      List<SampleOrSubsampleFileProperty> sampleFileProperties) {
    List<FileProperty> fileProperties = new ArrayList<>();
    for (SampleOrSubsampleFileProperty sampleFileProperty : sampleFileProperties) {
      fileProperties.add(sampleFileProperty.getImageFileProperty());
      fileProperties.add(sampleFileProperty.getThumbnailFileProperty());
    }
    return fileProperties;
  }
}
