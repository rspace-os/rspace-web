package com.researchspace.model;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.record.TestFactory;

public class CommunityTest {
	
	private Community community;
	private User admin1, admin2, normalUser, pi;
	private Group group;

	@BeforeEach
	public void setUp() throws Exception {
		admin1 = TestFactory.createAnyUser("admin");
		admin1.addRole(Role.ADMIN_ROLE);
		admin2 = TestFactory.createAnyUser("admin2");
		admin2.addRole(Role.ADMIN_ROLE);
		normalUser = TestFactory.createAnyUser("user");
		normalUser.addRole(Role.USER_ROLE);
		pi = TestFactory.createAnyUser("pi");
		pi.addRole(Role.PI_ROLE);
		group = TestFactory.createAnyGroup(pi, normalUser);
	}

	@Test
	public void testCopyConstructor (){
		community = new Community();
		community.addAdmin(admin1);
		community.setUniqueName("id");
		community.setProfileText("profile");
		community.setDisplayName("display");
		community.setId(1L);
		community.addLabGroup(group);
		
		Community copy  = new Community(admin1, community);
		// id isn't copied
		assertNull(copy.getId());
		// unique name is the same
		assertEquals(copy, community);
		// basic properties are copied
		assertEquals("profile", copy.getProfileText());
		assertEquals("display", copy.getDisplayName());
		// collections not copied
		assertEquals(1, community.getLabGroups().size());
		assertEquals(0, copy.getLabGroups().size());
	}

	@Test
	public void testCommunityThrowsIAEIfIdEmptyString() {
		community = new Community();
		community.setUniqueName("");
		assertIllegalArgumentException(()->new Community(admin1, community));
	}
	
	@Test
	public void testCommunityThrowsIAEIfAdminNotAnAdmin() {
		community = new Community();
		community.setUniqueName("id");
		assertIllegalArgumentException(()->new Community(normalUser, community));
	}

	@Test
	public void testGetSetDisplayNameTruncatesTooLongStrings() {
		String TOO_LONG = RandomStringUtils.randomAlphanumeric(Community.MAX_DESC_LENGTH + 1);
		community = new Community();
		community.setDisplayName(TOO_LONG);
		assertEquals(Community.MAX_DESC_LENGTH, community.getDisplayName().length());
	}

	@Test
	public void testGetCreationDateIsImmutable() {
		community = new Community();
		Date creationDate = community.getCreationDate();
		Date toMutate = community.getCreationDate();
		toMutate.setHours(creationDate.getHours() + 5);// alter the date
		Date creationDate2 = community.getCreationDate();
		assertEquals(creationDate, creationDate2);
	}

	@Test
	public void testAddAdminMustBeAnAdminRole() {
		community = new Community();
		assertIllegalArgumentException(()->community.addAdmin(normalUser));
	
	}

	@Test
	public void testAddAdmin() {
		community = new Community();
		assertTrue(community.addAdmin(admin2));
		assertNotNull(community.getPermissionsAdapter());
	}

	@Test
	public void testRemoveAdmin() {
		community = new Community();
		community.addAdmin(admin1);
		assertFalse(community.removeAdmin(admin2)); // not in admin collection, should just return false
		//can't remove the only admin
		assertFalse(community.removeAdmin(admin1));
		//add another
		community.addAdmin(admin2);
		assertEquals(2, community.getAdmins().size());
		//now can remove admin1
		assertTrue(community.removeAdmin(admin1));	
	}
	
	@Test
	public void testAddRemoveLabGroups() {
		community = new Community();
		community.setUniqueName("id");
		Community community2 = new Community();
		community2.setUniqueName("id2");
		
		Group g = TestFactory.createAnyGroup(pi, null);
		assertEquals(0, community.getLabGroups().size());

		// can't add collaboration group
		g.setGroupType(GroupType.COLLABORATION_GROUP);
		assertFalse(community.addLabGroup(g));
		assertEquals(0, community.getLabGroups().size());
		// trying to remove group not in collection is OK
		assertFalse(community.removeLabGroup(g));
		// now try with a LabGroup
		g.setGroupType(GroupType.LAB_GROUP);
		assertTrue(community.addLabGroup(g));
		assertEquals(1, community.getLabGroups().size());
		// can't add > 1
		assertThrows(IllegalArgumentException.class,()->community2.addLabGroup(g) );
		
		assertTrue(community.removeLabGroup(g));
		assertEquals(0, community.getLabGroups().size());
	}

	@Test
	public void testEqualsObject() {
		Community c1 = new Community();
		c1.setUniqueName("id");
		Community c2 = new Community();
		c2.setUniqueName("id");
		assertTrue(c1.equals(c2));
		assertEquals(c1.hashCode(),c2.hashCode());
		
		c2.setUniqueName("newid");
		assertFalse(c1.equals(c2));
	}

}
