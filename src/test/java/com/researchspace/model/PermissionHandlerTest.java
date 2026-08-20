package com.researchspace.model;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class PermissionHandlerTest {

	private PermissionHandler permHandler = new PermissionHandler();
	
	@Test
	public void testSettingPermissionString() {
		
		Set<String> permissionStrings = new HashSet<>();
		permissionStrings.add("FORM:CREATE:");
		permissionStrings.add("COMMS:READ:property_name=REQUESTEXTERNALSHARE");
		permissionStrings.add("COMMS:READ:property_name=REQUESTJOINEXISTINGCOLLABGROUP");
		
		permHandler.setPermissionStrings(permissionStrings);
		assertEquals("invariants not kept", permHandler.getPermissions().size(), permHandler.getPermissionStrings().size());
	}

}
