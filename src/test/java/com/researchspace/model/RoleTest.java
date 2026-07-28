package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleTest {
  Role role = null;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testEqualsHashcode() {
    role = new Role("ROLE_USER");
    Role role2 = new Role("ROLE_USER");
    // other role
    Role role3 = new Role("ROLE_ADMIN");

    assertEquals(role, role2);
    assertEquals(role.hashCode(), role2.hashCode());

    assertFalse(role.equals(role3));
    assertFalse(role.hashCode() == role3.hashCode());
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
  public void testRoleStringThrowsIAEIfUnknownRoleName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Role("unknownrole");
        });
  }

  @Test
  public void testAddRemovePermission() {
    role = new Role("ROLE_USER");
    ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
    ConstraintBasedPermission cbp = parser.resolvePermission("FORM:READ");
    role.addPermission(cbp);
    assertTrue(role.getPermissions().contains(cbp));
    role.removePermission(cbp);
    assertFalse(role.getPermissions().contains(cbp));
  }
}
