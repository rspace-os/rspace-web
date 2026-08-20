package com.researchspace.model.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CommunityListResultTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	// test various valid user inputs can be handled correctly
	@Test
	public void testValidateMultiCommunityAutocompleteInput() {
		String [] inputs = new String [] {"All Groups <-1>, name2 <567>", "name1<123>, name2<567>,","name1<123>, name2<567>","name1<1>, name2<567>","name2 <123>, name2 <567>"};
		for (String in: inputs) {
			assertTrue(CommunityListResult.validateMultiCommunityAutocompleteInput(in));
			Set<Long> ids2 = CommunityListResult
					.getCommunityIdsfromMultiGroupAutocomplete(in);
			assertEquals(2, ids2.size());
			assertTrue(ids2.contains(567L));
		}
	}

}
