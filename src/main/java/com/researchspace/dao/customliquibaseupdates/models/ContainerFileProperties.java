package com.researchspace.dao.customliquibaseupdates.models;

import com.researchspace.model.FileProperty;
import lombok.Data;

@Data
public class ContainerFileProperties {
  FileProperty imageFileProperty;
  FileProperty thumbnailFileProperty;
  FileProperty locationsImageFileProperty;

  public ContainerFileProperties() {}

  public ContainerFileProperties(
      FileProperty imageFileProperty,
      FileProperty thumbnailFileProperty,
      FileProperty locationsImageFileProperty) {
    if (imageFileProperty != null) {
      this.imageFileProperty = imageFileProperty;
    }

    if (thumbnailFileProperty != null) {
      this.thumbnailFileProperty = thumbnailFileProperty;
    }

    if (locationsImageFileProperty != null) {
      this.locationsImageFileProperty = locationsImageFileProperty;
    }
  }
}
