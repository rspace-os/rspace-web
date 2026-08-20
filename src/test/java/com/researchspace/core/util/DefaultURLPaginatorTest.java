package com.researchspace.core.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DefaultURLPaginatorTest {

	public class TestSearchObject extends FilterCriteria {
		@UISearchTerm
		private String field1;
		@UISearchTerm
		private String field2;

		public String getField1() {
			return field1;
		}

		public void setField1(String field1) {
			this.field1 = field1;
		}

		public String getField2() {
			return field2;
		}

		public void setField2(String field2) {
			this.field2 = field2;
		}
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGenerateURL() {
		IPagination<Object> pgCrit = BasicPaginationCriteria.createDefaultForClass(Object.class);
		TestSearchObject sObj = new TestSearchObject();
		sObj.setField1("f1");
		sObj.setField2("f2");
		pgCrit.setSearchCriteria(sObj);
		DefaultURLPaginator dURLpag = new DefaultURLPaginator("/some/path", pgCrit);
		assertEquals("/some/path?pageNumber=0&resultsPerPage=" + IPagination.DEFAULT_RESULTS_PERPAGE
				+ "&sortOrder=DESC&field1=f1&field2=f2", dURLpag.generateURL(0));

		pgCrit.setSearchCriteria(null);
		// now set search to null
		assertEquals(
				"/some/path?pageNumber=0&resultsPerPage=" + IPagination.DEFAULT_RESULTS_PERPAGE + "&sortOrder=DESC",
				dURLpag.generateURL(0));

		// no pagination set, just returns path
		DefaultURLPaginator dURLpag2 = new DefaultURLPaginator("/some/path", null);
		assertEquals("/some/path?", dURLpag2.generateURL(0));

	}

	@Test
	public void testGenerateREcordPerPageUR() {
		IPagination<Object> pgCrit = BasicPaginationCriteria.createDefaultForClass(Object.class);
		assertNotNull(pgCrit);
		DefaultURLPaginator dURLpag2 = new DefaultURLPaginator("/some/path", pgCrit);

		assertEquals(
				"/some/path?pageNumber=0&resultsPerPage=" + IPagination.DEFAULT_RESULTS_PERPAGE + "&sortOrder=DESC",
				dURLpag2.generateURL(0));
		assertEquals("/some/path?pageNumber=0&resultsPerPage=23&sortOrder=DESC",
				dURLpag2.generateURLForRecordsPerPage(23));

		// asseert null pgCrit handled gracefully
		dURLpag2 = new DefaultURLPaginator("/some/path", null);
		assertEquals("/some/path?", dURLpag2.generateURLForRecordsPerPage(IPagination.DEFAULT_RESULTS_PERPAGE));
	}
}
