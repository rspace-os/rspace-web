package com.researchspace.service.archive.export;

import static com.researchspace.model.comms.NotificationType.PROCESS_COMPLETED;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArchiveRemoverTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock CommunicationManager comms;
  @Mock IPropertyHolder properties;
  @Mock ArchiveExportServiceManager archiveMgr;
  public @Rule TemporaryFolder tempFolder = new TemporaryFolder();
  ArchivalCheckSum archivalChecksum = null;
  User anyUser = null;

  @InjectMocks ArchiveRemover remover;

  @Before
  public void setUp() throws Exception {
    remover.setRemovalPolicy(ExportRemovalPolicy.TRUE);
    archivalChecksum = TestFactory.createAnArchivalChecksum();
    anyUser = TestFactory.createAnyUser("any");
    archivalChecksum.setExporter(anyUser);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void noCurrentArchivesToBeDeletedHandledGracefully() {
    mockValidExportLocation();
    mockNoArchivesToBeDeleted();
    remover.removeOldArchives(archiveMgr);
    assertNoRemovalAttempted();
  }

  @Test
  public void noEligibleArchivesToBeDeletedHandledGracefully() {
    mockValidExportLocation();
    remover.setRemovalPolicy(ExportRemovalPolicy.FALSE);
    mockArchiveToBeDeleted();
    remover.removeOldArchives(archiveMgr);
    assertNoRemovalAttempted();
  }

  @Test // tests IO Exception handlin
  public void eligibleArchivesNotDeletedHandledAsFileDidNotExist() {
    mockValidExportLocation();
    mockArchiveToBeDeleted();
    remover.removeOldArchives(archiveMgr);
    assertNoRemovalAttempted();
  }

  @Test
  public void eligibleArchivesDeleted() throws IOException {
    mockValidExportLocation();
    mockArchiveToBeDeleted();
    FileUtils.write(
        new File(tempFolder.getRoot().getAbsolutePath(), archivalChecksum.getZipName()),
        "content",
        "UTF-8");
    remover.removeOldArchives(archiveMgr);
    assertRemovalSucceeded();
  }

  private void assertRemovalSucceeded() {
    verify(comms)
        .systemNotify(
            Mockito.eq(PROCESS_COMPLETED),
            Mockito.anyString(),
            Mockito.eq(archivalChecksum.getExporter().getUsername()),
            Mockito.eq(true));
    assertTrue(archivalChecksum.isDownloadTimeExpired());
    verify(archiveMgr).save(Mockito.any(ArchivalCheckSum.class));
  }

  @Test(expected = IllegalStateException.class)
  public void nonexistentArchiveLocation() {
    when(properties.getExportFolderLocation()).thenReturn("nonExistent");
    remover.removeOldArchives(archiveMgr);
  }

  @Test(expected = IllegalStateException.class)
  public void archiveLocationIsFile() throws IOException {
    when(properties.getExportFolderLocation())
        .thenReturn(File.createTempFile("any", "something").getAbsolutePath());
    remover.removeOldArchives(archiveMgr);
  }

  private void assertNoRemovalAttempted() {
    Mockito.verifyZeroInteractions(comms);
    Mockito.verify(archiveMgr, Mockito.never()).save(Mockito.any(ArchivalCheckSum.class));
  }

  private void mockNoArchivesToBeDeleted() {
    when(archiveMgr.getCurrentArchiveMetadatas()).thenReturn(Collections.emptyList());
  }

  private void mockArchiveToBeDeleted() {
    when(archiveMgr.getCurrentArchiveMetadatas())
        .thenReturn(TransformerUtils.toList(archivalChecksum));
  }

  private void mockValidExportLocation() {
    when(properties.getExportFolderLocation()).thenReturn(tempFolder.getRoot().getAbsolutePath());
  }
}
