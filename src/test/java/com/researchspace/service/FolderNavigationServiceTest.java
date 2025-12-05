package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.TestFactory;
import com.researchspace.service.impl.FolderNavigationServiceImpl;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FolderNavigationServiceTest {

  @Mock private PermissionUtils permissionUtils;
  @InjectMocks private FolderNavigationServiceImpl service;

  private User subject;

  @BeforeEach
  public void setUp() {
    subject = TestFactory.createAnyUser("testUser");
  }

  @Test
  public void whenUserOwnsParentReturnsParent() {
    Folder parentFolder = TestFactory.createAFolder("parent", subject);
    Folder childFolder = TestFactory.createAFolder("child", subject);
    parentFolder.addChild(childFolder, subject);

    Optional<Folder> result = service.findParentForUser(subject, childFolder);

    assertTrue(result.isPresent());
    assertEquals(parentFolder.getId(), result.get().getId());
  }

  @Test
  public void whenUserHasSharedAccessToParentThenReturnsParent() {
    User otherUser = TestFactory.createAnyUser("otherUser");
    Folder sharedFolder = TestFactory.createAFolder("sharedFolder", subject);
    sharedFolder.addType(RecordType.SHARED_FOLDER);

    Folder childFolder = TestFactory.createAFolder("child", subject);
    sharedFolder.addChild(childFolder, subject);

    when(permissionUtils.isPermitted(sharedFolder, PermissionType.READ, otherUser))
        .thenReturn(true);

    Optional<Folder> result = service.findParentForUser(otherUser, childFolder);

    assertTrue(result.isPresent());
    assertEquals(sharedFolder.getId(), result.get().getId());
  }

  @Test
  public void whenUserHasNoAccessToParentThenReturnsEmpty() {
    User otherUser = TestFactory.createAnyUser("otherUser");
    Folder parentFolder = TestFactory.createAFolder("parent", subject);
    Folder childFolder = TestFactory.createAFolder("child", subject);
    parentFolder.addChild(childFolder, subject);

    Optional<Folder> result = service.findParentForUser(otherUser, childFolder);

    assertFalse(result.isPresent());
  }

  @Test
  public void whenSharedFolderHasMultipleParentsThenReturnsCorrectParent() {
    User userA = TestFactory.createAnyUser("userA");
    User userB = TestFactory.createAnyUser("userB");
    User piUser = TestFactory.createAnyUserWithRole("piUser", Role.PI_ROLE.getName());

    Group group = TestFactory.createAnyGroup(piUser, userA);
    group.addMember(userB, RoleInGroup.DEFAULT);
    UserGroup userAGroup = new UserGroup(userA, group, RoleInGroup.DEFAULT);
    UserGroup userBGroup = new UserGroup(userB, group, RoleInGroup.DEFAULT);
    userA.setUserGroups(Collections.singleton(userAGroup));
    userB.setUserGroups(Collections.singleton(userBGroup));

    // Create Lab Groups folders for both users (owned by each user)
    Folder piLabGroups = TestFactory.createAFolder("pi_LabGroups", piUser);
    Folder userALabGroups = TestFactory.createAFolder("userA_LabGroups", userA);
    Folder userBLabGroups = TestFactory.createAFolder("userB_LabGroups", userB);

    // Create the communal group shared folder (owned by sysadmin)
    Folder groupSharedFolder =
        TestFactory.createAFolder(
            "groupA_SHARED",
            TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName()));
    groupSharedFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    group.setCommunalGroupFolderId(groupSharedFolder.getId());

    // Add the shared folder as a child of each users' Lab Groups folders
    piLabGroups.addChild(groupSharedFolder, piUser);
    userALabGroups.addChild(groupSharedFolder, userA);
    userBLabGroups.addChild(groupSharedFolder, userB);

    Optional<Folder> resultPi = service.findParentForUser(piUser, groupSharedFolder);
    assertEquals(piLabGroups.getId(), resultPi.get().getId());
    assertEquals(piUser, resultPi.get().getOwner());

    Optional<Folder> resultA = service.findParentForUser(userA, groupSharedFolder);
    assertEquals(userALabGroups.getId(), resultA.get().getId());
    assertEquals(userA, resultA.get().getOwner());

    Optional<Folder> resultB = service.findParentForUser(userB, groupSharedFolder);
    assertEquals(userBLabGroups.getId(), resultB.get().getId());
    assertEquals(userB, resultB.get().getOwner());
  }

  @Test
  public void buildPathToRootReturnsCompletePath() {
    Folder root = TestFactory.createAFolder("root", subject);
    Folder level1 = TestFactory.createAFolder("level1", subject);
    Folder level2 = TestFactory.createAFolder("level2", subject);
    root.addChild(level1, subject);
    level1.addChild(level2, subject);

    List<Folder> path = service.buildPathToRootFolder(level2, subject, null);

    assertEquals(2, path.size());
    assertEquals(level1.getId(), path.get(0).getId());
    assertEquals(root.getId(), path.get(1).getId());
  }

  @Test
  public void whenPathToRootFindGalleryRootThenStops() {
    Folder userRoot = TestFactory.createAFolder("UserRoot", subject);
    Folder galleryRoot = TestFactory.createAFolder("Gallery", subject);
    galleryRoot.addType(RecordType.ROOT_MEDIA);
    userRoot.addChild(galleryRoot, subject);

    Folder gallerySubfolder = TestFactory.createAFolder("subfolder", subject);
    galleryRoot.addChild(gallerySubfolder, subject);

    List<Folder> path = service.buildPathToRootFolder(gallerySubfolder, subject, null);

    assertEquals(1, path.size());
    assertEquals(galleryRoot.getId(), path.get(0).getId());
  }

  @Test
  public void whenParentIdIsInvalidThenThrowsException() {
    Folder actualParent = TestFactory.createAFolder("actualParent", subject);
    Folder childFolder = TestFactory.createAFolder("child", subject);
    actualParent.addChild(childFolder, subject);

    Folder wrongParent = TestFactory.createAFolder("wrongParent", subject);

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          service.findParentForUser(wrongParent.getId(), subject, childFolder);
        });
  }
}
