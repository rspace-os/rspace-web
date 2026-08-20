package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;

public class UserTest {
	User u1;
	Group gp1;
	@Before
	public void setUp() throws Exception {
		u1= new User("user1");
		gp1 =new Group("group1",u1);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testIsPermitted() {
		ConstraintBasedPermission p1 = createAndConfigureAPermission();
		
		u1.addPermission(p1);
		//matches whether or not inherit is true, since does not belong to group
		assertTrue(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
		assertTrue(u1.isPermitted(getMatchingEntityPermissionFromRecord(), true));

		u1.removePermission(p1);
		assertFalse(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
	}

	public ConstraintBasedPermission createAndConfigureAPermission() {
		// create READ Only permission for docs  created in a range owned by 'user'
		ConstraintBasedPermission p1 = new ConstraintBasedPermission(PermissionDomain.RECORD, 
				ConstraintBasedPermissionTest.createPermissonActionSet(PermissionType.READ));
		PropertyConstraint pc = ConstraintBasedPermissionTest.createAPropertyConstraint("ownedBy", "user");
		p1.addPropertyConstraint(pc);
		return p1;
	}
	
	@Test
	public void testIsPermittedInheritance() {
        ConstraintBasedPermission p1 = createAndConfigureAPermission();
		//permission added to inherited group
		gp1.addPermission(p1);
		gp1.addMember(u1,RoleInGroup.DEFAULT);
		//matches only if  inherit is true
		assertFalse(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
		assertTrue(u1.isPermitted(getMatchingEntityPermissionFromRecord(), true));
		p1.setEnabled(false);
		assertFalse(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
		p1.setEnabled(true);
		gp1.removePermission(p1);
		assertFalse(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
		
		
	}
	EntityPermission getMatchingEntityPermissionFromRecord() {
		EntityPermission ep = new EntityPermission(PermissionDomain.RECORD,
			PermissionType.READ);
		ep.addPropertyConstraint( ConstraintBasedPermissionTest.createAPropertyConstraint("ownedBy", "user"));
		return ep;
	}
	
	@Test
	public void testGetAllPermissions(){
		ConstraintPermissionResolver resolver = new ConstraintPermissionResolver();
		ConstraintBasedPermission p1 = resolver.resolvePermission("RECORD:READ:id=1,2,3");
		ConstraintBasedPermission p2 = resolver.resolvePermission("RECORD:CREATE:id=1,2,3");

		ConstraintBasedPermission p3 = resolver.resolvePermission("RECORD:WRITE:id=1,2,3");
		
		gp1.addPermission(p1);
		gp1.addPermission(p2);
		u1.addPermission(p3);
		gp1.addMember(u1,RoleInGroup.DEFAULT);
		// get all, inherited
		assertEquals(3,u1.getAllPermissions(true, true).size());
		// don't inherit
		assertEquals(1,u1.getAllPermissions(true, false).size());
		p1.setEnabled(false);
		//disable permission from group
		assertEquals(2,u1.getAllPermissions(false, true).size());
	}

}
