package com.researchspace.model;



import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.permissions.PermissionType;

public class AccessControlTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testIsAllowed() {
		assertTrue(AccessControl.isAllowed(PermissionType.WRITE, PermissionType.READ));
		assertTrue(AccessControl.isAllowed(PermissionType.WRITE, PermissionType.WRITE));
		assertFalse(AccessControl.isAllowed(PermissionType.READ, PermissionType.WRITE));
		assertFalse(AccessControl.isAllowed(PermissionType.NONE, PermissionType.READ));
		// export not considered only read and write 
		assertFalse(AccessControl.isAllowed(PermissionType.WRITE, PermissionType.EXPORT));
	}

}
