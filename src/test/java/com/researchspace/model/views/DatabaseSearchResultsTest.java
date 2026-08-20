package com.researchspace.model.views;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PaginationCriteria;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.researchspace.model.PaginationCriteria.createDefaultForClass;
import static org.junit.Assert.*;

public class DatabaseSearchResultsTest {

	ISearchResults<?> api;
	List<Object> ANYLIST = Collections.emptyList();

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetResults() {
		api = new SearchResultsImpl<>(ANYLIST, 0, 0L);
		assertNotNull(api.getResults());
	}
	
	@Test
	public void testGetFirstLastResult() {
		PaginationCriteria<Object> pgcrit = createDefaultForClass(Object.class);
		api = new SearchResultsImpl<>(ANYLIST,pgcrit,0L);
		assertNull(api.getFirstResult());
		assertNull(api.getLastResult());
		
		Object o1 = new Object();
		Object o2 = new Object();
		api = new SearchResultsImpl<>(TransformerUtils.toList(o1,o2),pgcrit,0L);
		assertEquals(o1, api.getFirstResult());
		assertEquals(o2,api.getLastResult());		
	}


	@Test
	public void testGetTotalHits() {
		api = new SearchResultsImpl<>(ANYLIST, 0, 500L);
		assertEquals(500, api.getTotalHits().longValue());
	}

	@Test
	public void testGetHits() {
		api = new SearchResultsImpl<>(Collections.singletonList(new Object()), 0,
				500L);
		assertEquals(1, api.getHits().intValue());
	}

	@Test
	public void testGetPageNumber() {
		api = new SearchResultsImpl<>(Collections.singletonList(new Object()), 5,
				500L);
		assertEquals(5, api.getPageNumber().intValue());
	}

	@Test
	public void testGetTotalPages() {
		int EXPECTED1 = 50; // at 10 records/page
		api = new SearchResultsImpl<>(ANYLIST, 5, 500L);
		assertEquals(EXPECTED1, api.getTotalPages().intValue());

		int EXPECTED2 = 51; // at 10 records/page
		api = new SearchResultsImpl<>(ANYLIST, 5, 501L);
		assertEquals(EXPECTED2, api.getTotalPages().intValue());

		// no pages if no results
		int EXPECTED3 = 0; // at 10 records/page
		api = new SearchResultsImpl<>(ANYLIST, 5, 0L);
		assertEquals(EXPECTED3, api.getTotalPages().intValue());
	}

}
