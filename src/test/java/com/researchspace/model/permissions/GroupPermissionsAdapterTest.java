package com.researchspace.model.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;

public class GroupPermissionsAdapterTest {

	GroupPermissionsAdapter gpa;
	@Before
	public  void setUp() {
	}

	@Test
	public void testGroupCommunityPermissions() {
		User p1 = TestFactory.createAnyUserWithRole("any", Constants.PI_ROLE);
		Group g1 = TestFactory.createAnyGroup(p1, new User []{});
		g1.setCommunityId(3L);
		gpa = new GroupPermissionsAdapter(g1);
		gpa.setAction(PermissionType.WRITE);
		
		ConstraintBasedPermission OKtestPerm =  new ConstraintPermissionResolver().resolvePermission("GROUP:WRITE:communityId=3");
		assertTrue(OKtestPerm.implies(gpa));
		
		ConstraintBasedPermission BADIDtestPerm =  new ConstraintPermissionResolver().resolvePermission("GROUP:WRITE:communityId=4");
		assertFalse(BADIDtestPerm.implies(gpa));
		
		ConstraintBasedPermission WRITEALL =  new ConstraintPermissionResolver().resolvePermission("GROUP:WRITE");
		assertTrue(WRITEALL.implies(gpa));
	}

}
