package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.Record;
import com.researchspace.service.exceptions.RecordCopyException;
import com.researchspace.service.impl.DocumentCopyManagerImpl;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DocumentCopyManagerTest {

  @InjectMocks private DocumentCopyManagerImpl documentCopyManager;

  @Mock private RecordDao recordDao;
  @Mock private FolderDao folderDao;
  @Mock private IPermissionUtils permissionUtils;
  @Mock private FileStore fileStore;

  private User user;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(recordDao.get(anyLong())).thenReturn(getImageMediaFile());
    when(recordDao.save(any(Record.class))).thenReturn(null);
    when(permissionUtils.isPermitted(any(Record.class), eq(PermissionType.COPY), eq(user)))
        .thenReturn(true);
  }

  @Test
  void testCopyMediaFileUnableToRetrieveOriginal() {
    when(fileStore.retrieve(any())).thenReturn(Optional.empty());
    EcatMediaFile mediaFile = getImageMediaFile();
    assertThrows(
        RecordCopyException.class,
        () -> documentCopyManager.copy(mediaFile, "test-image_copy", user, mediaFile.getParent()));
  }

  @Test
  void testCopyMediaFileUnableToSaveNewCopy() throws IOException {
    FileInputStream fileInputStream = new FileInputStream(RSpaceTestUtils.getAnyAttachment());
    when(fileStore.retrieve(any())).thenReturn(Optional.of(fileInputStream));
    when(fileStore.save(any(), any(), any(), any())).thenThrow(IOException.class);
    EcatMediaFile mediaFile = getImageMediaFile();
    assertThrows(
        RecordCopyException.class,
        () -> documentCopyManager.copy(mediaFile, "test-image_copy", user, mediaFile.getParent()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "550e8400-e29b-41d4-a716-446655440000",
        "550e8400-e29b-41d4-a716-446655440000.png"
      })
  void copiesCollisionGeneratedFilenames(String storedName) throws IOException {
    EcatMediaFile mediaFile = getImageMediaFile();
    mediaFile.getFileProperty().setFileName(storedName);
    when(recordDao.get(anyLong())).thenReturn(mediaFile);
    when(recordDao.save(any(Record.class))).thenAnswer(invocation -> invocation.getArgument(0));
    try (FileInputStream input = new FileInputStream(RSpaceTestUtils.getAnyAttachment())) {
      when(fileStore.retrieve(any())).thenReturn(Optional.of(input));
      when(fileStore.save(any(), any(), any(), any())).thenReturn(URI.create("file:/copy"));
      documentCopyManager.copy(mediaFile, "copy", user, null);
      ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
      verify(fileStore)
          .save(
              any(FileProperty.class),
              same(input),
              name.capture(),
              eq(FileDuplicateStrategy.AS_NEW));
      assertTrue(name.getValue().startsWith(FilenameUtils.getBaseName(storedName) + "_"));
      assertEquals(
          FilenameUtils.getExtension(storedName), FilenameUtils.getExtension(name.getValue()));
    }
  }

  private EcatImage getImageMediaFile() {
    EcatImage mediaFile = new EcatImage();

    mediaFile.setEditInfo(getNewEditInfo());
    mediaFile.setFileName("test-image.png");
    mediaFile.setId(1L);
    mediaFile.setFileProperty(getNewFileProperty());
    return mediaFile;
  }

  private EditInfo getNewEditInfo() {
    EditInfo editInfo = new EditInfo();
    editInfo.setName("test-image");
    editInfo.setCreationDate(new Date());
    return editInfo;
  }

  private FileProperty getNewFileProperty() {
    FileProperty fileProperty = new FileProperty();
    fileProperty.setFileName("test-image.png");
    return fileProperty;
  }
}
