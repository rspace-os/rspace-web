package com.researchspace.api.v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;

public class ApiFolderParentTest {

  private User owner;
  private User otherUser;

  @Before
  public void setUp() {
    owner = TestFactory.createAnyUser("owner");
    otherUser = TestFactory.createAnyUser("otherUser");
  }

  @Test
  public void whenUserOwnsParentFolderThenParentSet() {
    Folder parentFolder = TestFactory.createAFolder("parent", owner);
    Folder childFolder = TestFactory.createAFolder("child", owner);
    parentFolder.addChild(childFolder, owner);

    ApiFolder apiFolder = new ApiFolder(childFolder, owner);

    assertEquals(parentFolder.getId(), apiFolder.getParentFolderId());
  }

  @Test
  public void whenUserHasSharedParentFolderThenParentSet() {
    Folder ownerParent = TestFactory.createAFolder("parentA", owner);
    Folder otherUserParent = TestFactory.createAFolder("parentB", otherUser);
    Folder childFolder = TestFactory.createAFolder("child", owner);
    // childFolder has both parents
    ownerParent.addChild(childFolder, owner);
    otherUserParent.addChild(childFolder, owner);

    ApiFolder apiFolder = new ApiFolder(childFolder, otherUser);

    assertEquals(otherUserParent.getId(), apiFolder.getParentFolderId());
  }

  @Test
  public void testParentIdNullWhenUserHasNoParent() {
    Folder parentFolder = TestFactory.createAFolder("parent", owner);
    Folder childFolder = TestFactory.createAFolder("child", owner);
    parentFolder.addChild(childFolder, owner);

    ApiFolder apiFolder = new ApiFolder(childFolder, otherUser);

    assertNull(apiFolder.getParentFolderId());
  }

  @Test
  public void testPathToRootFolder() {
    Folder root = TestFactory.createAFolder("root", owner);
    Folder level1 = TestFactory.createAFolder("level1", owner);
    Folder level2 = TestFactory.createAFolder("level2", owner);
    root.addChild(level1, owner);
    level1.addChild(level2, owner);

    ApiFolder apiFolder = new ApiFolder(level2, true, owner);

    assertNotNull(apiFolder.getPathToRootFolder());
    assertEquals(2, apiFolder.getPathToRootFolder().size());
  }

  @Test
  public void testFolderWithNoParentsIsNull() {
    Folder rootFolder = TestFactory.createAFolder("root", owner);

    ApiFolder apiFolder = new ApiFolder(rootFolder, owner);

    assertNull(apiFolder.getParentFolderId());
  }
}
