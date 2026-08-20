package com.researchspace.model.events;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;


class UserAccountEventTest {

	@Test
	void equalsHashcodeDependsOnTimestampFail() throws InterruptedException {
		User anyUser = TestFactory.createAnyUser("any");
		UserAccountEvent e1 = new UserAccountEvent( anyUser, AccountEventType.DISABLED);
		Thread.sleep(1);
		UserAccountEvent e2 = new UserAccountEvent(anyUser, AccountEventType.DISABLED);
		assertThat(e1, not(equalTo(e2)));
		assertThat(e1.hashCode(), not(equalTo(e2.hashCode())));
	}
	
	@Test
	void equalsHashcodeDependsOnTimestamp() throws InterruptedException {
		User anyUser = TestFactory.createAnyUser("any");
		Date nowInstant = new Date();
		UserAccountEvent e1 = new UserAccountEvent(1L, anyUser, AccountEventType.DISABLED, nowInstant);
		Thread.sleep(1);
		UserAccountEvent e2 = new UserAccountEvent(1L, anyUser, AccountEventType.DISABLED, nowInstant);
		assertThat(e1, equalTo(e2));
		assertThat(e1.hashCode(), equalTo(e2.hashCode()));
	}

}
