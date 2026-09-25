package com.researchspace.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PermissionHandlerTest {

  private PermissionHandler permHandler = new PermissionHandler();

  @Test
  public void testSettingPermissionString() {

    Set<String> permissionStrings = new HashSet<>();
    permissionStrings.add("FORM:CREATE:");
    permissionStrings.add("COMMS:READ:property_name=REQUESTEXTERNALSHARE");
    permissionStrings.add("COMMS:READ:property_name=REQUESTJOINEXISTINGCOLLABGROUP");

    permHandler.setPermissionStrings(permissionStrings);
    assertThat(permHandler.getPermissionStrings())
        .as("invariants not kept")
        .hasSize(permHandler.getPermissions().size());
  }
}
