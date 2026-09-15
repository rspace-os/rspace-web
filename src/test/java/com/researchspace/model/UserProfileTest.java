package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.record.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserProfileTest {

  User user;
  User other;

  @BeforeEach
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    other = TestFactory.createAnyUser("other");
  }

  @AfterEach
  public void tearDown() {}

  @Test
  public void testHashCodeEquals() {
    UserProfile profile = new UserProfile(user);
    UserProfile profile2 = new UserProfile(user);
    UserProfile otherProfile = new UserProfile(other);
    assertEquals(profile.hashCode(), profile2.hashCode());
    assertEquals(profile, profile2);
    assertFalse(otherProfile.hashCode() == profile.hashCode());
    assertFalse(otherProfile.equals(profile));
  }

  @Test
  public void testUserProfileUserThrowsIAEIfNullUser() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new UserProfile(null);
        });
  }

  @Test
  public void testSetOwner() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          UserProfile profile = new UserProfile(user);
          profile.setOwner(null);
        });
  }
}
