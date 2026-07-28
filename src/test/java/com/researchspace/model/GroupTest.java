package com.researchspace.model;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class GroupTest {

	Group group;
	User pi1, u2, u3;

	@BeforeEach
	public void setUp() throws Exception {
		group = new Group("group");
		pi1 = new User("u1");
		pi1.addRole(Role.PI_ROLE);
		pi1.setLastName("some");
		u2 = new User("u2");
		u3 = new User("u3");
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testSetSelfService() {
		assertFalse(group.isSelfService());
		group.setSelfService(true);
		assertTrue(group.isSelfService());
		Group collab = new Group();
		collab.setGroupType(GroupType.COLLABORATION_GROUP);
		assertFalse(collab.isSelfService());
		collab.setSelfService(false);
		assertThrows(IllegalArgumentException.class, ()->collab.setSelfService(true));
	}

	@Test
	public void testGrpFolderStatus() {
		//defaults
		assertTrue(group.isGroupFolderWanted());
		assertFalse(group.isGroupFolderCreated());
		group.setGroupFolderCreated();
		assertTrue(group.isGroupFolderCreated());
	}

	@Test
	public void getMembers() {
		assertNotNull(group.getMembers());
	}

	@Test
	public void getSize() {
		group.addMember(pi1, RoleInGroup.PI);
		assertTrue(group.hasPIs());
		group.addMember(u2, RoleInGroup.RS_LAB_ADMIN);
		group.addMember(u3, RoleInGroup.DEFAULT);
		assertEquals(3, group.getSize());
		assertEquals(3, group.getEnabledMemberSize());
		assertEquals(0, group.getDisabledMemberSize());
		u2.setEnabled(false);
		assertEquals(3, group.getSize());
		assertEquals(2, group.getEnabledMemberSize());
		assertEquals(1, group.getDisabledMemberSize());
	}

	@Test
	void testAddMember() {
		group.addMember(pi1);
		assertEquals(1, group.getSize());
		assertTrue(group.hasMember(pi1));
		assertTrue(pi1.hasGroup(group));
	}

	@Test
	void testAddMembersAndRoles() {
		assertFalse(group.hasPIs());
		group.addMember(pi1, RoleInGroup.PI);
		assertTrue(group.hasPIs());
		group.addMember(u2, RoleInGroup.RS_LAB_ADMIN);
		group.addMember(u3, RoleInGroup.DEFAULT);
		assertEquals(1, group.getPiusers().size());
		assertEquals(1, group.getAdminUsers().size());
		assertEquals(1, group.getDefaultUsers().size());
		assertEquals(2, group.getUsersByRole(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN).size());

		assertEquals(RoleInGroup.RS_LAB_ADMIN, group.getRoleForUser(u2));
		assertEquals(RoleInGroup.PI, group.getRoleForUser(pi1));
	}

	@Test
	void testGroupOwnerCanBeOnlyAddedToProjectGroup() {
		var e = assertThrows(IllegalArgumentException.class, () -> group.addMember(u2, RoleInGroup.GROUP_OWNER));
		assertEquals("Attempting to add group owner to group other than Project Group", e.getMessage());


	}
	@Test
	void testGetGroupOwnerUsersReturnsGroupOwnerSet() {
		group.setGroupType(GroupType.PROJECT_GROUP);
		group.addMember(u2, RoleInGroup.GROUP_OWNER);
		assertEquals(1, group.getGroupOwnerUsers().size());
	}

	@Test
	void testEqualsObject() {
		Group newGr = new Group(group.getUniqueName());
		assertEquals(newGr, group);
	}

	@Test
	void testIsGroup() {
		assertTrue(group.isGroup());
		assertFalse(pi1.isGroup());
	}

	@Test
	void getAdminsWithViewAll() {
		group.addMember(u2, RoleInGroup.DEFAULT);
		group.addMember(u3, RoleInGroup.RS_LAB_ADMIN);
		group.addMember(pi1, RoleInGroup.PI);
		group.setLabAdminViewAll(u3, true);
		assertEquals(1, group.getLabAdminsWithViewAllPermission().size());
		assertEquals(u3, group.getLabAdminsWithViewAllPermission().iterator().next());
	}

	@Test
	void getMEmbersWithViewAll() {
		group.addMember(u2, RoleInGroup.DEFAULT);
		group.addMember(pi1, RoleInGroup.PI);
		group.addMember(u3, RoleInGroup.RS_LAB_ADMIN);
		group.setLabAdminViewAll(u3, true);
		// pi + u3
		assertEquals(2, group.getMembersWithDefaultViewAllPermissions().size());
	}

	@Test
	void testIsUser() {
		assertFalse(group.isUser());
		assertTrue(pi1.isUser());
	}

	@Test
	void testGetUniqueMembers() {
		Group g1 = new Group("g1");
		Group g2 = new Group("g2");
		Set<Group> grps = new HashSet<>();
		assertTrue(g1.isEmpty());
		g1.addMember(pi1);
		assertFalse(g1.isEmpty());
		g2.addMember(pi1);
		grps.add(g2);
		grps.add(g1);
		assertEquals(1, Group.getUniqueUsersInGroups(grps, null).size());
		g1.addMember(u2);
		assertEquals(2, Group.getUniqueUsersInGroups(grps, User.LAST_NAME_COMPARATOR).size());
		//exclude u2
		assertEquals(1, Group.getUniqueUsersInGroups(grps, User.LAST_NAME_COMPARATOR, u2).size());
	}

	@Test
	void testCannotAddUserToSameGroupTwice() {
		group.addMember(pi1, RoleInGroup.RS_LAB_ADMIN);
		assertEquals(1, group.getMembers().size());
		group.addMember(pi1, RoleInGroup.RS_LAB_ADMIN);
		assertEquals(1, group.getMembers().size());//unchanged
	}

	@Test
	void testcreateAndSaveUserNAme() {
		Group g = new Group();
		assertNull(g.getUniqueName());
		g.setDisplayName("something");
		g.createAndSetUniqueGroupName();

		String TOO_LONG_NAME = RandomStringUtils.randomAlphabetic(256);
		g.setDisplayName(TOO_LONG_NAME);
		assertEquals(255, g.getDisplayName().length());


		String uniquename = g.getUniqueName();
		assertNotNull(uniquename);
		assertTrue(uniquename.contains("something"));
		//try again, username should remain the same
		g.createAndSetUniqueGroupName();
		assertEquals(uniquename, g.getUniqueName()); // all alphabetic chars removed

		Group g2 = new Group();
		assertNull(g2.getUniqueName());
		// check will work even if display name not set.
		g2.createAndSetUniqueGroupName();
		String uniquename2 = g2.getUniqueName();
		assertNotNull(uniquename2);
	}

	@Test
	void getPermAdaptorNotNull() {
		Group g = new Group("name");
		assertNotNull(g.getPermissionsAdapter());
		assertTrue(g.isLabGroup());// default
		assertFalse(g.isCollaborationGroup());
		g.setGroupType(GroupType.COLLABORATION_GROUP);
		assertTrue(g.isCollaborationGroup());
		assertFalse(g.isProjectGroup());
		assertFalse(g.isLabGroup());
	}

	@Test
	void testCreateAndSaveGroupNameUsesOnlyAlphabeticChars() {
		Group g1 = new Group();
		assertNull(g1.getUniqueName());
		g1.createAndSetUniqueGroupName();
		assertTrue(g1.getUniqueName().matches("^[A-Za-z0-9]+$"));// all non a

		Group g2 = new Group();
		g2.setDisplayName("someone's group");
		g2.createAndSetUniqueGroupName();
		assertTrue(g2.getUniqueName().matches("^[A-Za-z0-9]+$"));// all non a
	}

	@Test
	void testGetMemberGroupsForCollabGroup() {
		// Only collab groups should allow getting of the member groups
		Group labGroup = new Group();
		assertThrows(
				IllegalStateException.class,
				labGroup::getMemberGroupsForCollabGroup);
		User pi1 = new User("PiUser1");
		pi1.addRole(Role.PI_ROLE);
		pi1.setLastName("PI");
		Group pi1Group = new Group("Pi1Group");
		pi1Group.setOwner(pi1);
		pi1Group.addMember(pi1, RoleInGroup.PI);

		User pi2 = new User("PiUser2");
		pi2.addRole(Role.PI_ROLE);
		pi2.setLastName("PI");
		Group pi2Group = new Group("Pi2Group");
		pi2Group.setOwner(pi2);
		pi2Group.addMember(pi2, RoleInGroup.PI);

		Group collabGroup = new Group();
		collabGroup.setGroupType(GroupType.COLLABORATION_GROUP);
		collabGroup.setUniqueName("suchUniqueMuchWow");
		collabGroup.setOwner(pi1);
		collabGroup.addMember(pi1, RoleInGroup.PI);
		collabGroup.addMember(pi2, RoleInGroup.PI);

		// If each PI is only the PI of one group, all groups should be listed
		assertEquals(2, collabGroup.getMemberGroupsForCollabGroup().size());

		Group pi1Group2 = new Group("Pi1Group2");
		pi1Group2.setOwner(pi1);
		pi1Group2.addMember(pi1, RoleInGroup.PI);

		// Groups of PIs that have more than 1 group shouldn't be displayed
		assertEquals(1, collabGroup.getMemberGroupsForCollabGroup().size());

		User user1 = new User("User1");
		user1.addRole(Role.USER_ROLE);
		user1.setLastName("User1");
		pi1Group.addMember(user1);
		collabGroup.addMember(user1);

		User user2 = new User("User2");
		user2.addRole(Role.USER_ROLE);
		user2.setLastName("User2");
		pi2Group.addMember(user2);
		collabGroup.addMember(user2);

		// If a group has collaborating users, groups of collaborating users should be displayed
		assertEquals(2, collabGroup.getMemberGroupsForCollabGroup().size());

		// Groups from which the members are not in the collab group shouldn't be displayed
		User user3 = new User("User3");
		user3.addRole(Role.USER_ROLE);
		user3.setLastName("User3");
		pi1Group2.addMember(user3);

		assertEquals(2, collabGroup.getMemberGroupsForCollabGroup().size());
		assertFalse(collabGroup.getMemberGroupsForCollabGroup().contains(pi1Group2));
	}
}
