package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.model.EcatImage;
import com.researchspace.model.FileProperty;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;

class InventoryFileTest {

  @Test
  void shallowCopyKeepsGalleryFileAndFileProperty() {
    EcatImage galleryFile = TestFactory.createEcatImage(1L);
    FileProperty fileProperty = new FileProperty();
    fileProperty.setFileSize("10");
    galleryFile.setFileProperty(fileProperty);
    InventoryFile attachment = new InventoryFile(galleryFile);

    InventoryFile copy = attachment.shallowCopy();

    assertSame(galleryFile, copy.getMediaFile());
    assertSame(fileProperty, copy.getFileProperty());
  }
}
