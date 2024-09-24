package com.researchspace.dao.customliquibaseupdates.hashfilecontents;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
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
    fileProperties.addAll(getSubSampleFileProperties());
    return fileProperties;
  }

  private List<FileProperty> getContainerFileProperties() {
    List<ImageThumbnailDTO> imageAndThumbnails =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new"
                    + " com.researchspace.dao.customliquibaseupdates.hashfilecontents.ImageThumbnailDTO(c.imageFileProperty,"
                    + " c.thumbnailFileProperty) from Container c",
                ImageThumbnailDTO.class)
            .list();

    // locationsImageFilProperty selected separately as it's often null, and trying to create
    // a DTO with image, thumbnail and location images in the case where location was null
    // wouldn't create a DTO entity even if image and thumbnail were present, so some FileProperty
    // would be missed.
    List<FileProperty> locations =
        sessionFactory
            .getCurrentSession()
            .createQuery("select locationsImageFileProperty from Container", FileProperty.class)
            .list();

    List<FileProperty> fileProperties = extractFilePropertiesFromDTO(imageAndThumbnails);
    fileProperties.addAll(locations);
    return fileProperties;
  }

  private List<FileProperty> getSampleFileProperties() {
    List<ImageThumbnailDTO> sampleFileProperties =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new com.researchspace.dao.customliquibaseupdates"
                    + ".hashfilecontents.ImageThumbnailDTO(s.imageFileProperty,"
                    + " s.thumbnailFileProperty) from Sample s",
                ImageThumbnailDTO.class)
            .list();

    return extractFilePropertiesFromDTO(sampleFileProperties);
  }

  private List<FileProperty> getSubSampleFileProperties() {
    List<ImageThumbnailDTO> subSampleFileProperties =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select new"
                    + " com.researchspace.dao.customliquibaseupdates.hashfilecontents.ImageThumbnailDTO(s.imageFileProperty,"
                    + " s.thumbnailFileProperty) from SubSample s",
                ImageThumbnailDTO.class)
            .list();

    return extractFilePropertiesFromDTO(subSampleFileProperties);
  }

  private static List<FileProperty> extractFilePropertiesFromDTO(
      List<ImageThumbnailDTO> imageThumbnailDTOs) {
    List<FileProperty> fileProperties = new ArrayList<>();
    for (ImageThumbnailDTO imageThumbnailDTO : imageThumbnailDTOs) {
      fileProperties.add(imageThumbnailDTO.getImageFileProperty());
      fileProperties.add(imageThumbnailDTO.getThumbnailFileProperty());
    }
    return fileProperties;
  }
}
