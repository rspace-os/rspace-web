package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.record.TestFactory;

public class RecordSharingACLTest {
	
	private ConstraintPermissionResolver permResolver;
	private ConstraintBasedPermission copy, create, write, read, multi;
	private Group g1, g2;
	private User piUserInGroup;
	private User notInGroup, alice;

	@Before
	public void setUp() throws Exception {
		permResolver = new ConstraintPermissionResolver();
		copy = permResolver.resolvePermission("RECORD:COPY");
		create = permResolver.resolvePermission("RECORD:CREATE");
		write = permResolver.resolvePermission("RECORD:WRITE");
		read = permResolver.resolvePermission("RECORD:READ");
		multi = permResolver.resolvePermission("RECORD:READ,DELETE");

		piUserInGroup = TestFactory.createAnyUser("userInGroup");
		piUserInGroup.addRole(Role.PI_ROLE);
		notInGroup = TestFactory.createAnyUser("notInGroup");
		alice = TestFactory.createAnyUser("alice");
		// add Alice to both groups.
		g1 = new Group("G1", piUserInGroup);
		g1.addMember(piUserInGroup, RoleInGroup.DEFAULT);
		g1.addMember(alice, RoleInGroup.DEFAULT);
		g2 = new Group("G2", piUserInGroup);
		g2.addMember(alice, RoleInGroup.DEFAULT);
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testIdentifyPartialMatch() {
		RecordSharingACL acl = new RecordSharingACL();
		acl.addACLElement(g1, multi);
		assertTrue(acl.isPermitted(alice, PermissionType.READ));
		assertTrue(acl.isPermitted(alice, PermissionType.DELETE));
	}
	
	@Test
	public void testRemoveAllUserPermissionsFromACL() {
		RecordSharingACL acl = new RecordSharingACL();
		acl.addACLElement(alice, multi);
		acl.addACLElement(piUserInGroup, multi);
		assertTrue(acl.getString().contains("alice"));
		acl.removeACLsforUserOrGroup(alice);
		assertFalse(acl.isPermitted(alice, PermissionType.READ));
		assertFalse(acl.isPermitted(alice, PermissionType.DELETE));
		assertFalse(acl.getString().contains("alice"));
		assertTrue(acl.isPermitted(piUserInGroup, PermissionType.READ));
		assertTrue(acl.isPermitted(piUserInGroup, PermissionType.DELETE));
	}

	// RSPAC-314
	@Test
	public void testUserIn2GroupsPermissionsWorks() {
		RecordSharingACL acl = new RecordSharingACL();
		acl.addACLElement(g1, read);
		acl.addACLElement(g2, read);
		assertTrue(acl.isPermitted(alice, PermissionType.READ));
		// check will work in either order
		RecordSharingACL acl2 = new RecordSharingACL();
		acl2.addACLElement(g2, read);
		acl2.addACLElement(g1, read);
		assertTrue(acl.isPermitted(alice, PermissionType.READ));
	}

	@Test
	public void testAddRemoveACLElement() {
		RecordSharingACL acl = new RecordSharingACL();

		assertTrue(acl.addACLElement(g1, copy));
		assertEquals("G1=" + copy.getString(), acl.getAcl());
		// try to add againg - duplicate should not be added
		assertFalse(acl.addACLElement(g1, copy));
		assertEquals("G1=" + copy.getString(), acl.getAcl());
		assertEquals(1, acl.getNumPermissions());

		// remove - now is empty string
		assertTrue(acl.removeACLElement(g1, copy));
		assertEquals("", acl.getAcl());

		// try to remove again- still is empty string
		assertFalse(acl.removeACLElement(g1, copy));
		assertEquals("", acl.getAcl());
		assertEquals(0, acl.getNumPermissions());

		// add > 1 element
		assertTrue(acl.addACLElement(g1, copy));
		assertTrue(acl.addACLElement(g1, create));
		String EXPECTED = "G1=" + copy.getString() + "&G1=" + create.getString();
		assertEquals(EXPECTED, acl.getAcl());
	}

	@Test
	public void testSettingFromStringRegeneratesList() {
		RecordSharingACL acl = new RecordSharingACL();
		String perm1 = "G1=RECORD:CREATE:";
		String perm2 = "G2=RECORD:CREATE:";
		String toSet = perm1 + RecordSharingACL.ACL_ELEMENT_DELIMITER + perm2;
		acl.setAcl(toSet);
		assertEquals(2, acl.getNumPermissions());
		assertEquals(toSet, acl.getString()); // test round-trip

		String toSet2 = "G3=RECORD:CREATE:";
		acl.setAcl(toSet2);
		// should overwrite,
		assertEquals(1, acl.getNumPermissions());
		assertEquals(toSet2, acl.getString()); // test round-trip
		// empty string or null can clear permissions
		acl.setAcl("");
		assertEquals(0, acl.getNumPermissions());
		acl.setAcl(null);
		assertEquals(0, acl.getNumPermissions());
	}

	@Test
	public void testCopy() {
		RecordSharingACL acl = RecordSharingACL.createACLForUserOrGroup(g1, PermissionType.COPY);
		assertTrue(acl.isPermitted(piUserInGroup, PermissionType.COPY));

		RecordSharingACL aclCopy = acl.copy();
		assertEquals(acl.getAcl(), aclCopy.getAcl());
	}

	@Test
	public void testImplies() {
		RecordSharingACL acl = new RecordSharingACL();

		assertTrue(acl.addACLElement(g1, copy));
		assertTrue(acl.isPermitted(piUserInGroup, copy.getAsEntityPermission().getAction()));
		assertFalse(acl.isPermitted(notInGroup, copy.getAsEntityPermission().getAction()));
		assertFalse(acl.isPermitted(piUserInGroup, create.getAsEntityPermission().getAction()));

		// add permission to individual user
		assertTrue(acl.addACLElement(notInGroup, copy));
		assertTrue(acl.isPermitted(notInGroup, copy.getAsEntityPermission().getAction()));
		assertFalse(acl.isPermitted(notInGroup, create.getAsEntityPermission().getAction()));
		// oringinal user still allowed
		assertTrue(acl.isPermitted(piUserInGroup, copy.getAsEntityPermission().getAction()));

		// check that assigned write permission implies read.
		assertTrue(acl.addACLElement(notInGroup, write));
		assertTrue(acl.isPermitted(notInGroup, read.getAsEntityPermission().getAction()));

		// clear should remove all permissions
		acl.clear();
		assertFalse(acl.isPermitted(notInGroup, copy.getAsEntityPermission().getAction()));
		assertFalse(acl.isPermitted(piUserInGroup, copy.getAsEntityPermission().getAction()));
	}

	@Test
	public void testSetRoleForGroup() {
		RecordSharingACL acl = new RecordSharingACL();
		for (UserGroup ug:  g1.getUserGroups()) {
			if (ug.getUser().equals(piUserInGroup)) {
				ug.setRoleInGroup(RoleInGroup.PI, Collections.EMPTY_SET);
			}
		}
		
		ACLElement el = ACLElement.createRoleRestrictedGroupACL(g1, copy, RoleInGroup.PI);
		acl.addACLElement(el);
		assertTrue(acl.isPermitted(piUserInGroup, PermissionType.COPY));
		assertFalse(acl.isPermitted(notInGroup, PermissionType.COPY));

		notInGroup.addRole(Role.PI_ROLE);
		// now add ano ther user as a PI
		g1.addMember(notInGroup, RoleInGroup.PI);
		assertTrue(acl.isPermitted(notInGroup, PermissionType.COPY));

		// now remove user from grp - should be excluded
		g1.removeMember(notInGroup);
		assertFalse(acl.isPermitted(notInGroup, PermissionType.COPY));
		// add back in a different role, shouldn't be authorized.
		g1.addMember(notInGroup, RoleInGroup.DEFAULT);
		assertFalse(acl.isPermitted(notInGroup, PermissionType.COPY));
	}
	
	@Test
	public void testRemovingRolePermission() {
		RecordSharingACL acl = new RecordSharingACL();
		acl.setAcl("u1=RECORD:WRITE:&g2=RECORD:READ:&g2[PI,RS_LAB_ADMIN]=RECORD:DELETE,FOLDER_RECEIVE:&g1=RECORD:WRITE:g1[PI,RS_LAB_ADMIN]=SEND,RENAME:");
		acl.removeACLsforRolesInGroup(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
		assertEquals("u1=RECORD:WRITE:&g2=RECORD:READ:&g1=RECORD:WRITE:", acl.getAcl());
	}

	@Test
	public void testClear() {
		RecordSharingACL acl = new RecordSharingACL();
		assertTrue(acl.addACLElement(g1, copy));
		assertEquals(1, acl.getNumPermissions());
		acl.clear();
		assertEquals(0, acl.getNumPermissions());
		assertEquals("", acl.getString());
	}
}
