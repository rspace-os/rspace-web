package com.researchspace.model.comms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.fortuna.ical4j.validate.ValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.record.TestFactory;

import net.fortuna.ical4j.model.Date;

public class ICSEventGeneratorTest {

	ICSEventGenerator icalgen;

	@Before
	public void setUp() throws Exception {
		icalgen = new ICSEventGenerator();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws IOException, ValidationException, URISyntaxException, ParseException {
		MessageOrRequest mor = CommsTestUtils.createARequest(TestFactory.createAnyUser("any"));
		mor.setMessage(" A message");
		Date future = new Date(Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());
		mor.setRequestedCompletionDate(future);
		net.fortuna.ical4j.model.Calendar cal = icalgen.createICalEventFor(mor);
		cal.validate();
		assertNotNull(cal);
		assertTrue(cal.getComponents().size() > 0);
	}

}
