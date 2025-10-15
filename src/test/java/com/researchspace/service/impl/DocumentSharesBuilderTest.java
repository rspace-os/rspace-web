package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.RecordInfoSharingInfo;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.mapping.DocumentSharesBuilder;
import com.researchspace.service.mapping.ShareLocationResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocumentSharesBuilderTest {

  @Mock ShareLocationResolver resolver;

  @InjectMocks DocumentSharesBuilder builder;

  @Test
  public void testFieldsMapped() {
    User sharer = TestFactory.createAnyUser("sharer");
    BaseRecord record = TestFactory.createAnyRecord(sharer);
    record.setId(1L);

    User recipientUser = TestFactory.createAnyUser("recipient");
    recipientUser.setId(2L);
    User pi = TestFactory.createAnyUserWithRole("pi", "ROLE_PI");
    Group recipientGroup = TestFactory.createAnyGroup(pi, sharer);
    recipientGroup.setId(3L);

    RecordGroupSharing groupShare = new RecordGroupSharing();
    ReflectionTestUtils.setField(groupShare, "id", 1L);
    groupShare.setSharedBy(sharer);
    groupShare.setSharee(recipientUser);
    groupShare.setPermType(PermissionType.WRITE);
    groupShare.setShared(record);

    RecordGroupSharing sharedViaNotebook = new RecordGroupSharing();
    ReflectionTestUtils.setField(sharedViaNotebook, "id", 2L);
    sharedViaNotebook.setSharedBy(sharer);
    sharedViaNotebook.setSharee(recipientGroup);
    sharedViaNotebook.setPermType(PermissionType.READ);
    sharedViaNotebook.setShared(record);

    Folder directLocation = new Folder();
    directLocation.setId(1000L);
    directLocation.setName("DirectLoc");

    Folder notebookLocation = new Folder();
    notebookLocation.setId(2000L);
    notebookLocation.setName("MyNotebook");

    Folder directRoot = new Folder();
    directRoot.setId(900L);
    directRoot.setName("SharedRoot");

    Folder groupRoot = new Folder();
    groupRoot.setId(800L);
    groupRoot.setName("Group_Shared");

    when(resolver.resolveLocation(groupShare, record)).thenReturn(directLocation);
    when(resolver.resolveLocation(sharedViaNotebook, record)).thenReturn(notebookLocation);

    directRoot.addChild(directLocation, sharer);
    groupRoot.addChild(notebookLocation, sharer);

    RSPath directPath = new RSPath(List.of(directRoot, directLocation));
    RSPath notebookPath = new RSPath(List.of(groupRoot, notebookLocation));

    when(resolver.resolvePath(groupShare, record)).thenReturn(directPath);
    when(resolver.resolvePath(sharedViaNotebook, record)).thenReturn(notebookPath);

    RecordInfoSharingInfo shares =
        new RecordInfoSharingInfo(List.of(groupShare), List.of(sharedViaNotebook));

    DocumentShares result = builder.assemble(record, shares);

    assertEquals(record.getId(), result.getSharedDocId());
    assertEquals(record.getName(), result.getSharedDocName());

    // assert direct shares mapped
    assertEquals(1, result.getDirectShares().size());
    DocumentShares.Share direct = result.getDirectShares().get(0);
    assertEquals(1L, direct.getShareId());
    assertEquals(DocumentShares.RecipientType.USER, direct.getRecipientType());
    assertEquals(DocumentShares.PermissionType.EDIT, direct.getPermission());
    assertEquals(1000L, direct.getParentId());
    assertEquals("SharedRoot/DirectLoc", direct.getPath());
    assertEquals(directRoot.getId(), direct.getGrandparentId());

    // assert implicit (notebook) shares mapped
    assertEquals(1, result.getNotebookShares().size());
    DocumentShares.Share viaNotebook = result.getNotebookShares().get(0);
    assertEquals(2L, viaNotebook.getShareId());
    assertEquals(DocumentShares.RecipientType.GROUP, viaNotebook.getRecipientType());
    assertEquals(DocumentShares.PermissionType.READ, viaNotebook.getPermission());
    assertEquals(2000L, viaNotebook.getParentId());
    assertEquals("Group_Shared/MyNotebook", viaNotebook.getPath());
    assertEquals(groupRoot.getId(), viaNotebook.getGrandparentId());
  }
}
