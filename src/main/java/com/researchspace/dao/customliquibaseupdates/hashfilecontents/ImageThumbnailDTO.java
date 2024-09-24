package com.researchspace.dao.customliquibaseupdates.hashfilecontents;

import com.researchspace.model.FileProperty;
import lombok.Data;

@Data
class ImageThumbnailDTO {
  FileProperty imageFileProperty;
  FileProperty thumbnailFileProperty;

  public ImageThumbnailDTO() {}

  public ImageThumbnailDTO(FileProperty imageFileProperty, FileProperty thumbnailFileProperty) {
    this.imageFileProperty = imageFileProperty;
    this.thumbnailFileProperty = thumbnailFileProperty;
  }
}
