package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.record.TestFactory;



public class UserGroupTest {

	User u,u2;
	Group g1;
	UserGroup usergroup=null;
	@Before
	public void setUp() throws Exception {
		u = TestFactory.createAnyUser("user");
		u2 = TestFactory.createAnyUser("u2");
		g1 = new Group("any");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testRoleAssignment() {
		usergroup=new UserGroup(u, g1, RoleInGroup.RS_LAB_ADMIN);
		assertTrue(usergroup.isAdminRole());
		usergroup=new UserGroup(u, g1, RoleInGroup.PI);
		assertTrue(usergroup.isPIRole());
	}
	
	@Test
	public void testEqualsHashCode() {
		usergroup=new UserGroup(u, g1, RoleInGroup.RS_LAB_ADMIN);
		
		UserGroup usergroup2=new UserGroup(u2, g1, RoleInGroup.PI);
		
		//test transitivity
		assertFalse(usergroup2.equals(usergroup));
		assertFalse(usergroup2.hashCode()==usergroup.hashCode());
		assertFalse(usergroup.equals(usergroup2));
		assertFalse(usergroup.hashCode()==usergroup2.hashCode());
		
		//equality based on user and group
		usergroup2.setUser(u);
		assertEquals(usergroup2, usergroup);
		assertEquals(usergroup2.hashCode(), usergroup.hashCode());
		
		//test set operations
		Set<UserGroup>set = new HashSet<>();
		usergroup2.setUser(u2);
		set.add(usergroup2);
		set.add(usergroup);
		assertEquals(2,set.size());
	}
	
	@Test
	public void testEqualsHashCode2() {
		UserGroup ug1 = new UserGroup();
		UserGroup ug2 = new UserGroup();
		assertEquals(ug1, ug2);
		Set<UserGroup>set = new HashSet<>();
		set.addAll(TransformerUtils.toList(ug1, ug2));
		assertEquals(1, set.size());
		// group is null
		ug1.setUser(u);
		ug2.setUser(u);
		assertEquals(1, set.size());
		
	}

	

}
