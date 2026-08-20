package com.researchspace.model.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultPermissionFactoryTest {

	private DefaultPermissionFactory fac = new DefaultPermissionFactory();
	private User pi = TestFactory.createAnyUser("u1");
	private User u2 = TestFactory.createAnyUser("u2");

	@Before
	public void setUp() {
		pi.addRole(Role.PI_ROLE);
	}

	@After
	public void tearDown() {
	}

	@Test
	public void setUpACLForGroupFolder() {
		Group grp = TestFactory.createAnyGroup(pi, new User[] {u2 });
		Folder f1 = TestFactory.createAFolder("any", pi);
		fac.setUpACLForGroupSharedRootFolder(grp, f1);
		// does not add permissions for a regular folder
		assertTrue(f1.getSharingACL().getNumPermissions() == 0);

		f1.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
		fac.setUpACLForGroupSharedRootFolder(grp, f1);
		assertTrue(f1.getSharingACL().getNumPermissions() > 0);
		//check user can create a folder.
		assertTrue(f1.getSharingACL().isPermitted(u2, PermissionType.CREATE_FOLDER));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void setUpACLForApiInboxFolderThrowsIAEIfNotAPiInboxFolder() {
		Folder folder = TestFactory.createAFolder("any", u2);
		fac.setUpAclForIndividualInboxFolder(folder, u2);
	}
	@Test(expected=AuthorizationException.class)
	public void setUpACLForApiInboxFolderThrowsAuthExceptionIfSubjectNotOwner() {
		Folder folder = TestFactory.createAnAPiInboxFolder( u2);
		fac.setUpAclForIndividualInboxFolder(folder, pi);
	}

	@Test
	public void setUpACLForApiInboxFolderAssertions() {
		Folder folder = TestFactory.createAnAPiInboxFolder( u2);
		fac.setUpAclForIndividualInboxFolder(folder, u2);
		assertTrue(folder.getSharingACL().isPermitted(u2, PermissionType.WRITE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void communityEditPermsCannotBeAddedtoANonAdminUser() {
		User u = TestFactory.createAnyUserWithRole("any", Constants.USER_ROLE);
		fac.createCommunityPermissionsForAdmin(u, new Community());
	}

	@Test
	public void communityEditPermsOnlyanbeAddedtoAdminUser() {
		final User u = TestFactory.createAnyUserWithRole("any", Constants.ADMIN_ROLE);
		Community comm = new Community();
		comm.setId(-1L);
		Set<ConstraintBasedPermission> cbps = fac
				.createCommunityPermissionsForAdmin(u, comm);
	
		assertTrue(cbps.stream().anyMatch(cbp->u.isPermitted(cbp, true)));

		ConstraintBasedPermission test = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		test.setCommunityConstraint(new CommunityConstraint(-1L));
		assertTrue(cbps.contains(test));
	}

	@Test
	public void setUpGroupPIpermissions() {
		Group grp = TestFactory.createAnyGroup(pi, new User[] {u2 });
		Set<ConstraintBasedPermission> defaultperms = fac
				.createDefaultGlobalGroupPermissions(grp);

		Set<ConstraintBasedPermission> perms = fac
				.createDefaultPermissionsForGroupPI(grp);
		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
		ConstraintBasedPermission cbp = parser
				.resolvePermission(String.format("COMMS:READ:property_name=REQUESTJOINLABGROUP&group=%s", grp.getUniqueName()));
		assertTrue(perms.contains(cbp));
		Set<ConstraintBasedPermission> perms2 = fac
				.createDefaultPermissionsForGroupAdmin(grp);
		assertTrue(perms2.contains(cbp));
		// regular user can't invite new members
		assertFalse(defaultperms.contains(cbp));
	}

	@Test
	public void setUpCollaborationGroupPIpermissions() {
		Group grp = TestFactory.createAnyGroup(pi, new User[] {u2 });
		Set<ConstraintBasedPermission> defaultperms = fac
				.createDefaultGlobalGroupPermissions(grp);

		Set<ConstraintBasedPermission> perms = fac
				.createDefaultPermissionsForCollabGroupPI(grp);

		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
		String expectedGroupPermission = "GROUP:READ,WRITE:group=" + grp.getUniqueName();
		ConstraintBasedPermission cbp = parser.resolvePermission(expectedGroupPermission);
		
		assertTrue("permissions don't allow group read", perms.contains(cbp));

		Set<ConstraintBasedPermission> perms2 = fac
				.createDefaultPermissionsForCollabGroupAdmin(grp);
		assertTrue(perms2.contains(cbp));

		// regular user can't invite new members
		assertFalse(defaultperms.contains(cbp));
	}

	@Test
	public void projectGroupHasPermissionsToReadWriteAndSendGroupRequestMessages() {
		User groupOwner = TestFactory.createAnyUser("Test User");
		Group group = createProjectGroup(groupOwner);

		Set<ConstraintBasedPermission> groupOwnerPerms = fac.createDefaultPermissionsForProjectGroupOwner(group);

		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();

		ConstraintBasedPermission expectedGroupPermissions = parser.resolvePermission("GROUP:READ,WRITE:group=" + group.getUniqueName());
		assertTrue(groupOwnerPerms.contains(expectedGroupPermissions));

		ConstraintBasedPermission expectedMessagePermissions = parser.resolvePermission("COMMS:READ:property_name=REQUESTJOINPROJECTGROUP&group=" + group.getUniqueName());
		assertTrue(groupOwnerPerms.contains(expectedMessagePermissions));
	}

	private static Group createProjectGroup(User groupOwner, User... users) {
		Group group = new Group("A Test Group", groupOwner);
		group.setGroupType(GroupType.PROJECT_GROUP);
		group.addMember(groupOwner, RoleInGroup.GROUP_OWNER);
		for (User u : users) {
			group.addMember(u, RoleInGroup.DEFAULT);
		}
		return group;
	}

	@Test
	public void projectGroupMembersHavePermissionToManageItemsInSharedFolder(){
		User groupOwner = TestFactory.createAnyUser("Test User");
		Group projectGroup = createProjectGroup(groupOwner, u2);

		Folder sharedGroupFolder = TestFactory.createAFolder("any", groupOwner);
		sharedGroupFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);

		fac.setUpACLForGroupSharedRootFolder(projectGroup, sharedGroupFolder);

		List<PermissionType> expectedPermissions = List.of(PermissionType.CREATE_FOLDER, PermissionType.DELETE,
				PermissionType.SEND, PermissionType.FOLDER_RECEIVE, PermissionType.RENAME);

		for(PermissionType permissionType: expectedPermissions){
			assertTrue(sharedGroupFolder.getSharingACL().isPermitted(groupOwner, permissionType));
			assertTrue(sharedGroupFolder.getSharingACL().isPermitted(u2, permissionType));
		}
	}

}
