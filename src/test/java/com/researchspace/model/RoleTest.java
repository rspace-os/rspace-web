package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;

public class RoleTest {
	Role role = null;

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testEqualsHashcode() {
		role = new Role("ROLE_USER");
		Role role2 = new Role("ROLE_USER");
		// other role
		Role role3 = new Role("ROLE_ADMIN");

		assertEquals(role,role2);
		assertEquals(role.hashCode(),role2.hashCode());
		
		assertFalse(role.equals(role3));
		assertFalse(role.hashCode()==role3.hashCode());
	}

	@Test
	public void testIsRoleStringIdentifiable() {
		assertFalse(Role.isRoleStringIdentifiable("UNKNOWN_ROLE"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_PI"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_USER"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_ADMIN"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_SYSADMIN"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_ANONYMOUS"));
		assertTrue(Role.isRoleStringIdentifiable("ROLE_GROUP_OWNER"));
	}

	@Test
	(expected=IllegalArgumentException.class)
	public void testRoleStringThrowsIAEIfUnknownRoleName() {
		new Role("unknownrole");
	}

	@Test
	public void testAddRemovePermission() {
		role = new Role("ROLE_USER");
		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
		ConstraintBasedPermission cbp=parser.resolvePermission("FORM:READ");
		role.addPermission(cbp);
		assertTrue(role.getPermissions().contains(cbp));
		role.removePermission(cbp);
		assertFalse(role.getPermissions().contains(cbp));
		
		
		
	}

	

}
