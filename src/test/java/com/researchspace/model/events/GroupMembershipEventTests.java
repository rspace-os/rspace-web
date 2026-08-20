package com.researchspace.model.events;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;


class GroupMembershipEventTests {

	@Test
	void equalsHashcodeDependsOnTimestampFail() throws InterruptedException {
		User anyUser = TestFactory.createAnyUser("any");
		User piUser = TestFactory.createAnyUserWithRole("any", Constants.PI_ROLE);
		Group g = TestFactory.createAnyGroup(piUser,new User [] {});
		GroupMembershipEvent e1 = new GroupMembershipEvent( anyUser, g, GroupEventType.JOIN);
		Thread.sleep(1);
		GroupMembershipEvent e2 = new GroupMembershipEvent(anyUser, g, GroupEventType.JOIN);
		assertThat(e1, not(equalTo(e2)));
		assertThat(e1.hashCode(), not(equalTo(e2.hashCode())));
	}
	
	@Test
	void equalsHashcodeDependsOnTimestamp() throws InterruptedException {
		User anyUser = TestFactory.createAnyUser("any");
		User piUser = TestFactory.createAnyUserWithRole("any", Constants.PI_ROLE);
		Group g = TestFactory.createAnyGroup(piUser,new User [] {});
		Date nowInstant = new Date();
		GroupMembershipEvent e1 = new GroupMembershipEvent(1L, anyUser, g, GroupEventType.JOIN, nowInstant);

		GroupMembershipEvent e2 = new GroupMembershipEvent(1L, anyUser, g, GroupEventType.JOIN, nowInstant);
		assertThat(e1, equalTo(e2));
		assertThat(e1.hashCode(), equalTo(e2.hashCode()));
	
	}

}
