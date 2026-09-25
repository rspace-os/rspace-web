package com.researchspace.service.archive.export;

import static com.researchspace.model.comms.NotificationType.PROCESS_COMPLETED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiveRemoverTest {
  @Mock CommunicationManager comms;
  @Mock IPropertyHolder properties;
  @Mock ArchiveExportServiceManager archiveMgr;
  @TempDir public File tempFolder;
  ArchivalCheckSum archivalChecksum = null;
  User anyUser = null;

  @InjectMocks ArchiveRemover remover;

  @BeforeEach
  public void setUp() throws Exception {
    remover.setRemovalPolicy(ExportRemovalPolicy.TRUE);
    archivalChecksum = TestFactory.createAnArchivalChecksum();
    anyUser = TestFactory.createAnyUser("any");
    archivalChecksum.setExporter(anyUser);
  }

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
        new File(tempFolder.getAbsolutePath(), archivalChecksum.getZipName()), "content", "UTF-8");
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

  @Test
  public void nonexistentArchiveLocation() {
    when(properties.getExportFolderLocation()).thenReturn("nonExistent");
    assertThrows(IllegalStateException.class, () -> remover.removeOldArchives(archiveMgr));
  }

  @Test
  public void archiveLocationIsFile() throws IOException {
    when(properties.getExportFolderLocation())
        .thenReturn(File.createTempFile("any", "something").getAbsolutePath());
    assertThrows(IllegalStateException.class, () -> remover.removeOldArchives(archiveMgr));
  }

  private void assertNoRemovalAttempted() {
    Mockito.verifyNoInteractions(comms);
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
    when(properties.getExportFolderLocation()).thenReturn(tempFolder.getAbsolutePath());
  }
}
