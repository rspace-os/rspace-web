package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.record.TestFactory;

public class UserProfileTest {

	User user;
	User other;

	@Before
	public void setUp() throws Exception {
		user = TestFactory.createAnyUser("any");
		other = TestFactory.createAnyUser("other");
	}

	@After
	public void tearDown() {
	}

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

	@Test(expected = IllegalArgumentException.class)
	public void testUserProfileUserThrowsIAEIfNullUser() {
		new UserProfile(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetOwner() {
		UserProfile profile = new UserProfile(user);
		profile.setOwner(null);
	}

}
