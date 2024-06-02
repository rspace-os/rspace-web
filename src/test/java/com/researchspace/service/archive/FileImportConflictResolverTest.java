package com.researchspace.service.archive;

import static org.junit.Assert.assertEquals;

import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class FileImportConflictResolverTest {

  ArchiveModelFactory factory;
  User anyUser;
  FileImportConflictResolver resolver = null;

  @Before
  public void setUp() throws Exception {
    factory = new ArchiveModelFactory();
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  public void testGetFileToImportForSinglePng() throws IOException {
    File PngFile = File.createTempFile("PNG", ".png");
    ArchivalGalleryMetaDataParserRef regularPng = createStandardPngGalleryRef(PngFile);
    resolver = new FileImportConflictResolver(regularPng);
    assertEquals(PngFile, resolver.getFileToImport());
  }

  @Test
  public void testGetFileToImportForTiffWhenPngChosen() throws IOException {
    File PngFile = File.createTempFile("File", ".png");
    File TiffFile = File.createTempFile("File", ".tiff");
    ArchivalGalleryMetaDataParserRef galleryMetadata = createStandardPngGalleryRef(PngFile);
    galleryMetadata.addFile(TiffFile);
    galleryMetadata.getGalleryXML().setExtension("tif");
    galleryMetadata.getGalleryXML().setName(TiffFile.getName());

    resolver = new FileImportConflictResolver(galleryMetadata);
    assertEquals(TiffFile, resolver.getFileToImport());
    assertEquals(TiffFile.getName(), galleryMetadata.getGalleryXML().getFileName());
  }

  @Test
  public void testGetFileToImportForTiffWhenTiffChosen() throws IOException {
    File PngFile = File.createTempFile("File", ".png");
    File TiffFile = File.createTempFile("File", ".tiff");
    ArchivalGalleryMetaDataParserRef galleryMetadata = createStandardPngGalleryRef(PngFile);
    galleryMetadata.getFileList().set(0, TiffFile); // this will be 1st retrieved
    galleryMetadata.getGalleryXML().setExtension("tif");
    galleryMetadata.getGalleryXML().setName(TiffFile.getName());

    resolver = new FileImportConflictResolver(galleryMetadata);
    assertEquals(TiffFile, resolver.getFileToImport());
    assertEquals(TiffFile.getName(), galleryMetadata.getGalleryXML().getFileName());
  }

  ArchivalGalleryMetaDataParserRef createStandardPngGalleryRef(File file) throws IOException {

    ArchivalGalleryMetaDataParserRef ref = new ArchivalGalleryMetaDataParserRef();
    ref.addFile(file);
    ArchivalGalleryMetadata xml = new ArchivalGalleryMetadata();

    EcatImage png = createAnECatImage();
    xml = factory.createGalleryMetadata(png);
    ref.setGalleryXML(xml);

    return ref;
  }

  private EcatImage createAnECatImage() {
    EcatImage image = TestFactory.createEcatImage(1L);
    Folder parent = TestFactory.createAFolder("parent", anyUser);
    parent.addChild(image, anyUser);
    parent.setId(-1L);
    return image;
  }
}
