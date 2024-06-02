package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
