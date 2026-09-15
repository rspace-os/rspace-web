package com.researchspace.model;

import static com.researchspace.model.RoleInGroup.getRoleFromString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class RoleInGroupTest {

  @Test
  public void getRoleFromString_groupOwnerStringReturnsGroupOwnerEnumValue() {
    assertEquals(RoleInGroup.GROUP_OWNER, getRoleFromString("group_owner"));
  }

  @Test
  public void getRoleFromString_LabAdminStringReturnsLabAdminEnumValue() {
    assertEquals(RoleInGroup.RS_LAB_ADMIN, getRoleFromString("rs_lab_admin"));
  }

  @Test
  public void getRoleFromString_PIStringReturnsPIEnumValue() {
    assertEquals(RoleInGroup.PI, getRoleFromString("pi"));
  }

  @Test
  public void getRoleFromString_lowercaseDefaultStringReturnsDefaultEnumValue() {
    assertEquals(RoleInGroup.DEFAULT, getRoleFromString("default"));
  }

  @Test
  public void getRoleFromString_unknownValueReturnsNull() {
    assertNull(getRoleFromString("unknown_role"));
  }
}
