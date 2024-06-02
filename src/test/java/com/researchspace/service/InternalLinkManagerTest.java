package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.PermissionsAdaptable;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.InternalLinkManagerImpl;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class InternalLinkManagerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock FieldDao fieldDao;
  @Mock BaseRecordManager baseRcdMgr;
  @Mock IPermissionUtils permUtils;
  @Mock InternalLinkDao internalLinkDao;
  @InjectMocks InternalLinkManagerImpl internalLinkMgr;

  User user;

  @Test
  public void saveInternalLinks() throws Exception {
    User user = TestFactory.createAnyUser("any");
    StructuredDocument doc = createADocument(user);
    Field field = getAField(doc);
    // can't be invalid target
    Snippet inValidTarget = TestFactory.createAnySnippet(user);
    inValidTarget.setId(3L);
    when(baseRcdMgr.get(inValidTarget.getId(), user)).thenReturn(inValidTarget);
    CoreTestUtils.assertExceptionThrown(
        () -> internalLinkMgr.createInternalLink(field.getId(), inValidTarget.getId(), user),
        IllegalArgumentException.class);

    // verify 3 types of linkable doc are accepted
    Folder folder = TestFactory.createAFolder("any", user);
    folder.setId(4L);
    Notebook notebook = TestFactory.createANotebook("nb", user);
    notebook.setId(5L);
    StructuredDocument docToLink = TestFactory.createAnySD();
    docToLink.setId(6L);
    when(fieldDao.get(field.getId())).thenReturn(field);

    // do 3 link creations, 1 for each acceptable type
    when(baseRcdMgr.get(Mockito.anyLong(), Mockito.eq(user)))
        .thenReturn(folder, notebook, docToLink);
    toList(folder, notebook, docToLink).stream()
        .forEach(br -> internalLinkMgr.createInternalLink(field.getId(), br.getId(), user));
    int expectedInvocationCount = 3;
    verify(internalLinkDao, Mockito.times(expectedInvocationCount))
        .saveInternalLink(Mockito.eq(doc.getId()), Mockito.anyLong());
    verify(permUtils, Mockito.times(expectedInvocationCount))
        .assertIsPermitted(
            Mockito.any(PermissionsAdaptable.class),
            Mockito.eq(PermissionType.READ),
            Mockito.eq(user),
            Mockito.anyString());
    verify(permUtils, Mockito.times(expectedInvocationCount))
        .assertIsPermitted(
            Mockito.eq(doc),
            Mockito.eq(PermissionType.WRITE),
            Mockito.eq(user),
            Mockito.anyString());
  }

  private Field getAField(StructuredDocument doc) {
    Field field = doc.getFields().get(0);
    field.setId(2L);
    return field;
  }

  private StructuredDocument createADocument(User user) {
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setOwner(user);
    doc.setId(1L);
    return doc;
  }
}
