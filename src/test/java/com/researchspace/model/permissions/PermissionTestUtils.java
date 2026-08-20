package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PermissionTestUtils {

	public static void assertPermissionsAreEquivalent(
			ConstraintBasedPermission p1, ConstraintBasedPermission p2) {
		if (p1.getIdConstraint() != null) {
			assertTrue(p1.getIdConstraint().getId().size() == p2
					.getIdConstraint().getId().size());
			assertTrue(p1.getIdConstraint().getString()
					.equals(p2.getIdConstraint().getString()));

		}
		assertTrue(p1.getPropertyConstraints().size() == p2
				.getPropertyConstraints().size());
		assertTrue(p1.getActions().size() == p2.getActions().size());
		assertTrue(p1.getLocationConstraints().size() == p2
				.getLocationConstraints().size());
		
		assertEquals(p1.getDomain(), p2.getDomain());
		assertEquals(p1.getGroupConstraint(), p2.getGroupConstraint());
		for (PermissionType lc : p1.getActions()) {

			boolean found = false;
			for (PermissionType lc2 : p2.getActions()) {
				if (lc2.equals(lc)) {
					found = true;
				}
			}
			if (!found) {
				fail("Could not find " + lc);
			}

		}
		for (PropertyConstraint pc : p1.getPropertyConstraints().values()) {
			assertEquals(pc.getString(),
					p2.getPropertyConstraints().get(pc.getName()).getString());
			assertTrue(p2.getPropertyConstraints().get(pc.getName()) != null);
		}

		for (LocationConstraint lc : p1.getLocationConstraints()) {

			boolean found = false;
			for (LocationConstraint lc2 : p2.getLocationConstraints()) {
				if (lc2.equals(lc)) {
					found = true;
				}
			}
			if (!found) {
				fail("Could not find " + lc);
			}

		}
	}

}
