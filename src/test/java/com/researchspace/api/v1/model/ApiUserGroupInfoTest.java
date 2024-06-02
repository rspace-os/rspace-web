package com.researchspace.api.v1.model;

import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static com.researchspace.model.record.TestFactory.createAnyUserWithRole;
import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiUserGroupInfoTest {

  @Test
  void testOrderByRoleAndName() {
    User pi = createAnyUserWithRole("ccc_pi", "ROLE_PI");
    User u1 = createAnyUser("bbb_labadmin");
    User u2 = createAnyUser("aaa_user");
    User u3 = createAnyUser("rrr_user");
    Group g = createAnyGroup(pi, u1, u2, u3);
    g.getUserGroupForUser(u1).setRoleInGroup(RoleInGroup.RS_LAB_ADMIN, emptySet());
    ApiGroupInfo groupInfo = new ApiGroupInfo(g);

    List<ApiUserGroupInfo> members = groupInfo.getMembers();
    /// Order by PI, Lab admin, users
    assertEquals("PI", members.get(0).getRole());
    assertEquals("LAB_ADMIN", members.get(1).getRole());
    assertEquals("aaa_user", members.get(2).getUsername());
    assertEquals("rrr_user", members.get(3).getUsername());
  }
}
