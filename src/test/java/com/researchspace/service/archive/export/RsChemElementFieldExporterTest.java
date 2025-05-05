package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class RsChemElementFieldExporterTest {

  private RsChemElementFieldExporter chemExporter;
  private ArchivalField archiveField;

  private FieldExportContext context;
  private ArchiveExportConfig exportConfig;
  private FieldExporterSupport support;
  private FileStore fileStore;

  private File archiveRecordFolder;

  @Before
  public void setUp() throws Exception {

    support = Mockito.mock(FieldExporterSupport.class);
    fileStore = Mockito.mock(FileStore.class);
    when(support.getFileStore()).thenReturn(fileStore);

    chemExporter = new RsChemElementFieldExporter(support);
    archiveField = new ArchivalField();
    archiveRecordFolder = Files.createTempDirectory("testArchive").toFile();
    context =
        new FieldExportContext(
            exportConfig, archiveField, archiveRecordFolder, null, null, null, null);
  }

  @Test
  public void checkPropsOfExportedChemElement() throws URISyntaxException, IOException {
    RSChemElement chem = new RSChemElement();
    chem.setId(1L);
    chem.setDataImage("dummy_image".getBytes());
    chem.setChemElementsFormat(ChemElementsFormat.MOL);

    final int initRecordFolderContentLength = archiveRecordFolder.listFiles().length;
    String replacementUrl = chemExporter.getReplacementUrl(context, chem);
    assertTrue(replacementUrl.matches("chem(.*)\\.png"));
    assertEquals(initRecordFolderContentLength + 1, archiveRecordFolder.listFiles().length);

    chemExporter.createFieldArchiveObject(chem, replacementUrl, context);
    ArchivalGalleryMetadata archivalChem = archiveField.getChemElementMeta().get(0);
    assertEquals(chem.getId(), archivalChem.getId());
    assertEquals(chem.getChemElementsFormat().name(), archivalChem.getChemElementsFormat());
    assertNotNull(archivalChem.getLinkFile());
    assertNull(archivalChem.getLinkToOriginalFile());
  }

  @Test
  public void checkPropsOfExportedChemElementWithPreview() throws URISyntaxException, IOException {

    byte[] smallImageBytes = "dummy_image".getBytes();
    byte[] largeImageBytes = "dummy_large_image".getBytes();

    // create chem with small image
    RSChemElement chem = new RSChemElement();
    chem.setId(1L);
    chem.setDataImage(smallImageBytes);
    chem.setChemElementsFormat(ChemElementsFormat.MOL);

    // mock larger image response
    FileProperty imageFileProperty = new FileProperty();
    chem.setImageFileProperty(imageFileProperty);
    Path imageFilePath = Files.createTempFile("largeImage", ".png");
    Files.write(imageFilePath, largeImageBytes);
    when(fileStore.findFile(imageFileProperty)).thenReturn(imageFilePath.toFile());

    final int initRecordFolderContentLength = archiveRecordFolder.listFiles().length;
    String replacementUrl = chemExporter.getReplacementUrl(context, chem);
    assertTrue(replacementUrl.matches("chem(.*)\\.png"));

    // verify two images are created in archive folder added
    assertEquals(initRecordFolderContentLength + 2, archiveRecordFolder.listFiles().length);
    File smallImageInArchive = new File(archiveRecordFolder, replacementUrl);
    assertTrue(smallImageInArchive.exists());
    assertEquals(smallImageBytes.length, smallImageInArchive.length());
    String largeImageFileName = chemExporter.getLargePreviewUrlForReplacementUrl(replacementUrl);
    File largeImageInArchive = new File(archiveRecordFolder, largeImageFileName);
    assertTrue(largeImageInArchive.exists());
    assertEquals(largeImageBytes.length, largeImageInArchive.length());

    chemExporter.createFieldArchiveObject(chem, replacementUrl, context);
    ArchivalGalleryMetadata archivalChem = archiveField.getChemElementMeta().get(0);
    assertEquals(replacementUrl, archivalChem.getLinkFile());
    assertEquals(largeImageFileName, archivalChem.getLinkToOriginalFile());
  }
}
