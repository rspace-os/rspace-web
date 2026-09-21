package com.researchspace.archive.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Community;
import com.researchspace.model.User;
import com.researchspace.testutils.ArchiveTestUtils;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public class ArchiveUsersTest {

  @Test
  public void testArchiveUsersRoundTrip() throws Exception {

    ArchiveUsersTestData testData = ArchiveTestUtils.createArchiveUsersTestData();

    ArchiveUsers fromXml = ArchiveTestUtils.writeToXMLAndReadFromXML(testData.getArchiveInfo());
    Iterator<User> it = fromXml.getUsers().iterator();
    assertThat(fromXml.getUserGroups()).hasSize(2);
    assertThat(fromXml.getGroups()).hasSize(1);
    assertThat(fromXml.getCommunities()).hasSize(1);
    User userRead = it.next();
    assertEquals(testData.getUser(), userRead);
    assertTrue(userRead.isInSameGroupAs(testData.getAdmin()));
    assertEquals(testData.getAdmin(), it.next());
    assertTrue(fromXml.getUserPreferences().iterator().next().getValueAsBoolean());
    // check communities
    Community inCommunity = fromXml.getCommunities().iterator().next();
    assertEquals(testData.getGroup(), inCommunity.getLabGroups().iterator().next());
    assertEquals(testData.getAdmin(), inCommunity.getAdmins().iterator().next());
    // profiles
    assertThat(fromXml.getProfiles()).hasSize(1);
  }
}
