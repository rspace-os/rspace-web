package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConstrainPermissionResolverTest {
	private static final String TEST_STRING2 = "Record:Read:id=1,2,3,4";
	private static final String TEST_STRING1 = "Record:Read:id=1,2,3&property_owner=user&location=/a/b/c*";
	private static final String TEST_STRING3 = "Record:Read,Write,Export:date_range=15-&id=1&property_owner=user&property_name=fred";
	private static final String TEST_STRING4 = "Record:Read,Write,Export:";
	private static final String TEST_STRING5 = "Record:Read,Write,Export:group=group1";
	private static final String TEST_STRING_COMMUNITY = "Record:Read,Write,Export:community=23";

                                           
	
	static final String [] TESTSTRINGS = new String [] {TEST_STRING1,TEST_STRING2,TEST_STRING3,TEST_STRING4,TEST_STRING5};

	ConstraintPermissionResolver resolver;
	@Before
	public void setUp() throws Exception {
		resolver = new ConstraintPermissionResolver();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testResolvePermission() {
		ConstraintBasedPermission p = resolver.resolvePermission(TEST_STRING1);
		LocationConstraint lc = new LocationConstraint("/a/b/c*");
		assertEquals(lc,p.getLocationConstraints().iterator().next() );

		PropertyConstraint EXPECTED_PROP_CONSTRAINT= new PropertyConstraint("owner", "user");
		assertEquals(EXPECTED_PROP_CONSTRAINT, p.getPropertyConstraints().get("owner"));
	}
	/*
	 * Tests that  String->Object->STring->Object conversions are consistent.
	 * String equality is too strict; a permission can be define with > 1 string (e.g., ordering is not important)
	 */
	@Test
	public void testStringRoundTrip (){
		for (String toTest: TESTSTRINGS){
			ConstraintBasedPermission p = resolver.resolvePermission(toTest);
			PermissionTestUtils.assertPermissionsAreEquivalent(p,resolver.resolvePermission(p.getString()));
		}
	}
	
	@Test
	public void testResolvePermissionWithGroups() {
		ConstraintBasedPermission p = resolver.resolvePermission(TEST_STRING5);
		GroupConstraint gc = p.getGroupConstraint();
		assertNotNull(gc);
		assertEquals(new GroupConstraint("group1"), gc);
	}
	
	@Test
	public void testResolvePermissionWithCommunities() {
		ConstraintBasedPermission p = resolver.resolvePermission(TEST_STRING_COMMUNITY);
		CommunityConstraint gc = p.getCommunityConstraint();
		assertNotNull(gc);
		assertEquals(new CommunityConstraint(23), gc);
		assertEquals("community=23", gc.getString());
	}
	@Test
	public void testResolvePermissionWithIds() {
		ConstraintBasedPermission p = resolver.resolvePermission(TEST_STRING2);
		IdConstraint idConstraint=p.getIdConstraint();
		assertEquals(4, idConstraint.getId().size());		
	}
	
	@Test
	public void testResolvePermissionAsEntityPermission() {
		 String toCheck = "Group:Write:group=abc";
		 String userPerm = "Group:Write:group=abc";
		 ConstraintBasedPermission toCheckObject = resolver.resolvePermission(toCheck);
		 ConstraintBasedPermission userObject = resolver.resolvePermission(userPerm);
		 assertTrue(userObject.implies(toCheckObject.getAsEntityPermission()));
		 
		 String toCheck2 = "Group:Write:group=def";
		 ConstraintBasedPermission toCheckObject2 = resolver.resolvePermission(toCheck2);
		 assertFalse(userObject.implies(toCheckObject2.getAsEntityPermission()));	 
	}
}
