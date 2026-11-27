package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.customliquibaseupdates.RemoveOrphanedAclEntries_RSDEV1636.Cleaned;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RemoveOrphanedAclEntries_RSDEV1636UnitTest {

  private static final Set<String> USERS = Set.of("alice", "odd[name]");
  private static final Set<String> GROUPS = Set.of("labGroup", "collab");

  private static Cleaned clean(String acl) {
    return RemoveOrphanedAclEntries_RSDEV1636.clean(
        acl, n -> USERS.contains(n) || GROUPS.contains(n), GROUPS::contains);
  }

  @Test
  void removesOnlyEntriesNamingNoPrincipalAndKeepsTheRestInOrder() {
    Cleaned result =
        clean(
            "labGroup=RECORD:READ:&ghost=RECORD:READ:|WRITE:&alice=RECORD:WRITE:"
                + "&labGroupx=RECORD:READ:");

    assertFalse(result.ambiguous());
    assertEquals("labGroup=RECORD:READ:&alice=RECORD:WRITE:", result.acl());
    assertEquals(List.of("ghost", "labGroupx"), result.removedNames());
  }

  @Test
  void keepsRoleListsOfExistingGroupsAndAnyUnprovableName() {
    String acl =
        "labGroup[PI,RS_LAB_ADMIN]=RECORD:READ:&collab[GROUP_OWNER]=RECORD:READ:"
            + "&ghost[PI]=RECORD:READ:&odd[name]=RECORD:READ:&rspace anonymous guest=RECORD:READ:";

    Cleaned result = clean(acl);

    assertFalse(result.isChanged());
    assertEquals(acl, result.acl());
  }

  @Test
  void keepsRoleListOfMissingGroup() {
    Cleaned result = clean("alice=RECORD:READ:&gone[PI]=RECORD:READ:&gone=RECORD:READ:");

    assertEquals("alice=RECORD:READ:&gone[PI]=RECORD:READ:", result.acl());
    assertEquals(List.of("gone"), result.removedNames());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ghost=RECORD:READ:&junk",
        "ghost=RECORD:READ:&a=b=c",
        "ghost=RECORD:READ:&=RECORD:READ:",
        "ghost=NOTAPERM",
        "ghost=RECORD",
        "ghost=RECORD:NOTANACTION:",
        "ghost=RECORD:READ:&&alice=RECORD:READ:",
        "&ghost=RECORD:READ:",
        "ghost=RECORD:READ:&"
      })
  void leavesUnparsedListsWhole(String acl) {
    Cleaned result = clean(acl);

    assertTrue(result.ambiguous());
    assertEquals(acl, result.acl());
    assertFalse(result.isChanged());
  }

  @Test
  void leavesPossiblyTruncatedListWhole() {
    String acl = "ghost=RECORD:READ:&" + "alice=RECORD:READ:&".repeat(140);
    acl = acl.substring(0, RemoveOrphanedAclEntries_RSDEV1636.ACL_COLUMN_LENGTH);

    assertTrue(clean(acl).ambiguous());
  }
}
