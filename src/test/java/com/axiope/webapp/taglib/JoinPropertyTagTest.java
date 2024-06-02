package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JoinPropertyTagTest {

  JoinPropertyStringFromCollection tag;

  @Before
  public void setUp() throws Exception {
    tag = new JoinPropertyStringFromCollection();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetStringBasic() {
    List<User> users = createUsers();
    tag.setCollection(users);
    // plain properties
    tag.setProperty("username");
    assertEquals("u1, u2, u3", tag.getOutputString());
    // also get methods
    tag.setProperty("fullNameAndEmail");
    assertEquals("first last (u1@b), first last (u2@b), first last (u3@b)", tag.getOutputString());
  }

  private List<User> createUsers() {
    User u1 = TestFactory.createAnyUser("u1");
    User u2 = TestFactory.createAnyUser("u2");
    User u3 = TestFactory.createAnyUser("u3");
    List<User> users = Arrays.asList(new User[] {u1, u2, u3});
    return users;
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetStringMaxSizeREquiresPositiveGt1() {
    tag.setMaxSize(0);
  }

  @Test
  public void testGetStringMaxSize() {
    List<User> users = createUsers();
    tag.setCollection(users);
    // plain properties
    tag.setProperty("username");
    assertEquals("u1, u2, u3", tag.getOutputString());
    // also get methods
    tag.setProperty("username");
    tag.setMaxSize(2);
    assertEquals("u1, u2...", tag.getOutputString());
    tag.setMaxSize(1);
    assertEquals("u1...", tag.getOutputString());
    tag.setMaxSize(500);
    assertEquals("u1, u2, u3", tag.getOutputString());
  }

  @Test
  public void checkContentEscaping_RSPAC_2262() {
    List<User> users = createUsers();
    users.get(0).setFirstName("\"><svg onload=alert(1)>");
    tag.setCollection(users);
    tag.setProperty("fullName");
    assertEquals(
        "&quot;&gt;&lt;svg onload=alert(1)&gt; last, first last, first last",
        tag.getOutputString());
  }
}
