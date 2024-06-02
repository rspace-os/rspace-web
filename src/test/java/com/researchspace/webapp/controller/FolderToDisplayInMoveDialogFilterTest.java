package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FolderToDisplayInMoveDialogFilterTest {

  FolderToDisplayInMoveDialogFilter filter = new FolderToDisplayInMoveDialogFilter();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFilter() throws IllegalAddChildOperation {
    User user = TestFactory.createAnyUser("auser");
    assertFalse(filter.filter(null));
    Folder f = TestFactory.createANotebook("any", user);
    assertFalse(filter.filter(f));

    Folder f2 = TestFactory.createAFolder("any", user);
    f2.setRecordDeleted(true);
    assertFalse(filter.filter(f2));

    f2.setRecordDeleted(false);
    f2.addType(RecordType.ROOT_MEDIA);
    assertFalse(filter.filter(f2));

    Folder f3 = TestFactory.createAFolder("any", user);
    f3.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);

    assertFalse(filter.filter(f3));

    Folder shared = TestFactory.createAFolder(Folder.SHARED_FOLDER_NAME, user);
    Folder root = TestFactory.createAFolder("ROOt", user);
    user.setRootFolder(root);
    root.addType(RecordType.ROOT);
    root.addChild(shared, user);

    assertFalse(filter.filter(root.getChildrens().iterator().next()));
    root.addChild(f2, user);
    f2.removeType(RecordType.ROOT_MEDIA);
    assertTrue(filter.filter(f2.getParents().iterator().next().getRecord()));
  }
}
