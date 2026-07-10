package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordApiManagerImplTest {

  @Mock private RecordManager recordManager;
  @Mock private FolderManager folderManager;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private RecordApiManagerImpl manager;

  @Test
  void reportsUnavailableFormWhenDocumentCreationReturnsNull() {
    ApiDocument document = new ApiDocument();
    User user = new User("user");
    RSForm form = new RSForm();
    form.setId(42L);
    Folder targetFolder = new Folder();
    targetFolder.setId(23L);
    when(folderManager.getApiUploadTargetFolder("", user, null)).thenReturn(targetFolder);
    when(messages.getResourceNotFoundMessage("Form", 42L)).thenReturn("form unavailable");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> manager.createNewDocument(document, form, user));

    assertEquals("form unavailable", exception.getMessage());
  }
}
