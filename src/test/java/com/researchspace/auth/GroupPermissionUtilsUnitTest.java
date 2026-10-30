package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.CommunityDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupPermissionUtilsUnitTest {

  private static final Long COMMUNITY_ID = 5L;

  @Mock private IPermissionUtils permissionUtils;
  @Mock private CommunityDao communityDao;
  @Mock private IPropertyHolder properties;

  @InjectMocks private GroupPermissionUtils groupPermUtils;

  private User pi;
  private User otherPi;
  private User member;
  private User otherMember;
  private Group group;

  @BeforeEach
  void setUp() {
    pi = TestFactory.createAnyUserWithRole("pi1411", Role.PI_ROLE.getName());
    otherPi = TestFactory.createAnyUserWithRole("otherPi1411", Role.PI_ROLE.getName());
    member = TestFactory.createAnyUser("member1411");
    otherMember = TestFactory.createAnyUser("otherMember1411");
    group = TestFactory.createAnyGroup(pi, member, otherMember);
  }

  private void makeCollaborationGroup() {
    group.setGroupType(GroupType.COLLABORATION_GROUP);
    group.addMember(otherPi, RoleInGroup.PI);
  }

  @Test
  void collabGroupMemberCannotAlterRoles() {
    makeCollaborationGroup();

    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, member, member));
    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, member, otherMember));
    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, member, pi));
    verifyNoInteractions(permissionUtils);
  }

  @Test
  void collabGroupPiCanAlterRoles() {
    makeCollaborationGroup();

    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, pi, member));
    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, otherPi, member));
    verifyNoInteractions(permissionUtils);
  }

  @Test
  void sysadminCanAlterCollabGroupRoles() {
    makeCollaborationGroup();
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin1411", Role.SYSTEM_ROLE.getName());

    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, sysadmin, member));
    verifyNoInteractions(permissionUtils, communityDao);
  }

  @Test
  void communityAdminCanAlterCollabGroupRolesWhenAPiLabIsInTheirCommunity() {
    makeCollaborationGroup();
    Group otherPiLab = TestFactory.createAnyGroup(otherPi);
    otherPiLab.setCommunityId(COMMUNITY_ID);
    User commAdmin = TestFactory.createAnyUserWithRole("admin1411", Role.ADMIN_ROLE.getName());
    when(communityDao.listCommunitiesForAdmin(commAdmin.getId()))
        .thenReturn(List.of(communityWithId(COMMUNITY_ID)));

    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, commAdmin, member));
    verifyNoInteractions(permissionUtils);
  }

  @Test
  void communityAdminCannotAlterCollabGroupRolesWithNoPiLabInTheirCommunity() {
    makeCollaborationGroup();
    Group otherPiLab = TestFactory.createAnyGroup(otherPi);
    otherPiLab.setCommunityId(COMMUNITY_ID + 1);
    User commAdmin = TestFactory.createAnyUserWithRole("admin1411", Role.ADMIN_ROLE.getName());
    when(communityDao.listCommunitiesForAdmin(commAdmin.getId()))
        .thenReturn(List.of(communityWithId(COMMUNITY_ID)));

    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, commAdmin, member));
    verifyNoInteractions(permissionUtils);
  }

  @Test
  void selfServiceLeaveIsOnlyForYourselfOnCloudOrInAProjectGroup() {
    when(properties.isCloud()).thenReturn(true);
    assertTrue(groupPermUtils.isSelfServiceLeave(member.getUsername(), member, group));
    assertFalse(groupPermUtils.isSelfServiceLeave(member.getUsername(), pi, group));

    when(properties.isCloud()).thenReturn(false);
    assertFalse(groupPermUtils.isSelfServiceLeave(member.getUsername(), member, group));
    group.setGroupType(GroupType.PROJECT_GROUP);
    assertTrue(groupPermUtils.isSelfServiceLeave(member.getUsername(), member, group));
  }

  private Community communityWithId(Long id) {
    Community community = new Community();
    community.setId(id);
    return community;
  }

  @Test
  void labGroupPiNeedsWritePermission() {
    when(permissionUtils.isPermitted(group, PermissionType.WRITE, pi)).thenReturn(false);
    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, pi, member));

    when(permissionUtils.isPermitted(group, PermissionType.WRITE, pi)).thenReturn(true);
    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, pi, member));
  }

  @Test
  void projectGroupOwnerCanAlterRoles() {
    User owner = TestFactory.createAnyUser("owner1411");
    group.setGroupType(GroupType.PROJECT_GROUP);
    group.addMember(owner, RoleInGroup.GROUP_OWNER);

    assertTrue(groupPermUtils.subjectCanAlterGroupRole(group, owner, member));
    assertFalse(groupPermUtils.subjectCanAlterGroupRole(group, member, owner));
  }
}
