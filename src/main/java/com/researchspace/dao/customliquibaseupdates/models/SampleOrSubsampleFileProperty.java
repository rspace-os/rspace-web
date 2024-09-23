package com.researchspace.dao.customliquibaseupdates.models;

import com.researchspace.model.FileProperty;
import lombok.Data;

@Data
public class SampleOrSubsampleFileProperty {
  FileProperty imageFileProperty;
  FileProperty thumbnailFileProperty;

  public SampleOrSubsampleFileProperty() {}

  public SampleOrSubsampleFileProperty(
      FileProperty imageFileProperty, FileProperty thumbnailFileProperty) {
    if (imageFileProperty != null) {
      this.imageFileProperty = imageFileProperty;
    }

    if (thumbnailFileProperty != null) {
      this.thumbnailFileProperty = thumbnailFileProperty;
    }
  }
}
