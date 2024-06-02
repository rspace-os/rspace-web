package com.researchspace.archive.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Community;
import com.researchspace.model.User;
import com.researchspace.model.UserProfile;
import com.researchspace.testutils.ArchiveTestUtils;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveUsersTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testArchiveUsersRoundTrip() throws Exception {

    ArchiveUsersTestData testData = ArchiveTestUtils.createArchiveUsersTestData();

    ArchiveUsers fromXml = ArchiveTestUtils.writeToXMLAndReadFromXML(testData.getArchiveInfo());
    Iterator<User> it = fromXml.getUsers().iterator();
    assertEquals(2, fromXml.getUserGroups().size());
    assertEquals(1, fromXml.getGroups().size());
    assertEquals(1, fromXml.getCommunities().size());
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
    UserProfile profileIn = fromXml.getProfiles().iterator().next();
    assertEquals("blah", profileIn.getProfileText());
    assertTrue(true);
  }
}
