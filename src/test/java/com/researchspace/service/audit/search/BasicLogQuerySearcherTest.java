package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.core.util.DateRange;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.function.Predicate;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BasicLogQuerySearcherTest {


	BasicLogQuerySearcher searcher;
	static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			BasicLogQuerySearcher.DATE_FORMAT);
	static final SimpleDateFormat inputFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	IPagination<AuditTrailSearchResult> pgCrit = BasicPaginationCriteria
			.createDefaultForClass(AuditTrailSearchResult.class);

	@BeforeEach
	public void setUp() {
		LogLineContentProvider cp = new LogLineContentProviderImpl();
		searcher = new BasicLogQuerySearcher( new LogFileTracker(cp), cp);
		pgCrit = BasicPaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class);
	}

	@AfterEach
	public void tearDown() {
	}

	
	@Test
	 void testFullSearchResultOrdering() throws ParseException {
		searcher.setLogFolder(new File("src/test/resources/TestResources/auditLogSearch"));
		searcher.setLogFilePrefix("RSLogs");
		AuditTrailSearchElement cfg = new AuditTrailSearchElement();
		pgCrit.setGetAllResults();

		// name ascending
		pgCrit.setOrderBy("username");
		pgCrit.setSortOrder(SortOrder.ASC);
		ISearchResults<AuditTrailSearchResult> results3 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals("user1a", results3.getFirstResult().getEvent().getSubject());
		Assertions.assertEquals("user2b", results3.getLastResult().getEvent().getSubject());
		// name descending
		pgCrit.setSortOrder(SortOrder.DESC);
		ISearchResults<AuditTrailSearchResult> results = searcher.search(pgCrit, cfg);
		Assertions.assertEquals("user2b", results.getFirstResult().getEvent().getSubject());
		Assertions.assertEquals("user1a", results.getLastResult().getEvent().getSubject());

		// action ascending
		pgCrit.setOrderBy("action");
		pgCrit.setSortOrder(SortOrder.ASC);
		ISearchResults<AuditTrailSearchResult> results4 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(AuditAction.CREATE, results4.getFirstResult().getEvent().getAction());
		Assertions.assertEquals(AuditAction.WRITE, results4.getLastResult().getEvent().getAction());

		// action ascending
		pgCrit.setSortOrder(SortOrder.DESC);
		ISearchResults<AuditTrailSearchResult> results5 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(AuditAction.WRITE, results5.getFirstResult().getEvent().getAction());
		Assertions.assertEquals(AuditAction.CREATE, results5.getLastResult().getEvent().getAction());

		// date ascending
		pgCrit.setOrderBy("date");
		pgCrit.setSortOrder(SortOrder.ASC);
		ISearchResults<AuditTrailSearchResult> results6 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(inputFormat.parse("2014-05-16 12:49:53").toString(), results6.getFirstResult()
				.getEvent().getTimestamp().toString());
		Assertions.assertEquals(inputFormat.parse("2014-05-19 16:06:27").toString(), results6.getLastResult()
				.getEvent().getTimestamp().toString());

		// date ascending
		pgCrit.setSortOrder(SortOrder.DESC);
		ISearchResults<AuditTrailSearchResult> results7 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(inputFormat.parse("2014-05-19 16:06:27").toString(), results7.getFirstResult()
				.getEvent().getTimestamp().toString());
		Assertions.assertEquals(inputFormat.parse("2014-05-16 12:49:53").toString(), results7.getLastResult()
				.getEvent().getTimestamp().toString());
		
		// check null is handled gracefully
		pgCrit.setOrderBy(null);
		searcher.search(pgCrit, cfg);

	}
	
	@Test
	 void testArguments1() {
		AuditTrailSearchElement cfg = new AuditTrailSearchElement();		
		assertThrows(IllegalArgumentException.class, ()->searcher.search(null, cfg));
	}
	
	@Test
	 void testArguments2() {
		assertThrows(IllegalArgumentException.class, ()->searcher.search(pgCrit, null));
	}
	
	@Test
	 void bothOperatorAndOperatedAsCanSeeAuditLog () {
		searcher.setLogFolder(new File("src/test/resources/TestResources/auditLogSearch"));
		searcher.setLogFilePrefix("RSLogs");
		AuditTrailSearchElement cfg = new AuditTrailSearchElement();
		cfg.addUsernameTerm("sysadmin1");
	
		ISearchResults<AuditTrailSearchResult> sysAdminresults = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(2, sysAdminresults.getResults().size());
		
		cfg.clear();
		cfg.addUsernameTerm("user1b");
		cfg.setDomains(EnumSet.of(AuditDomain.GROUP));
		ISearchResults<AuditTrailSearchResult> user1bResults2 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(1, user1bResults2.getResults().size());
		Assertions.assertEquals(user1bResults2.getResults().get(0),
				sysAdminresults.getResults().get(1));
		
	}

	@Test
	 void testFullSearch() {

		searcher.setLogFolder(new File("src/test/resources/TestResources/auditLogSearch"));
		searcher.setLogFilePrefix("RSLogs");
		AuditTrailSearchElement cfg = new AuditTrailSearchElement();
		cfg.addUsernameTerm("user1a");
		DateTime from = new DateTime(2014, 1, 1, 1, 1);
		DateTime to = new DateTime(2016, 1, 1, 1, 1);
		cfg.setDateRange(new DateRange(from.toDate(), to.toDate()));
		ISearchResults<AuditTrailSearchResult> results = searcher.search(
				BasicPaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class), cfg);
		Assertions.assertEquals(3, results.getResults().size());
		Assertions.assertEquals(3, results.getTotalHits().intValue());

		// date range is outside log range
		DateTime from2 = new DateTime(2015, 1, 1, 1, 1);
		cfg.setDateRange(new DateRange(from2.toDate(), to.toDate()));
		ISearchResults<AuditTrailSearchResult> results2 = searcher.search(
				BasicPaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class), cfg);
		Assertions.assertEquals(0, results2.getResults().size());
		Assertions.assertEquals(0, results2.getTotalHits().intValue());
		final int TOTAL_HITS = 28;
		// now check that date range is inclusive
		cfg.clear();
		DateTime from3 = new DateTime(2014, 5, 16,0,0,0);
		DateTime to3 = new DateTime(2019, 6, 18,23,0,0); 
		cfg.setDateRange(new DateRange(from3.toDate(), to3.toDate()));
		ISearchResults<AuditTrailSearchResult> results8 = searcher.search(
				BasicPaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class), cfg);
		Assertions.assertEquals(TOTAL_HITS, results8.getTotalHits().intValue());
		
		cfg.clear();
		cfg.setActions(EnumSet.of(AuditAction.DUPLICATE));
		cfg.addUsernameTerm("user2b");
		ISearchResults<AuditTrailSearchResult> results3 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(13, results3.getTotalHits().intValue());

		// now we just use defaults, we should get ALL results in the file
		// for all users
		cfg.clear();
		
		ISearchResults<AuditTrailSearchResult> results4 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(pgCrit.getResultsPerPage().intValue(), results4.getResults().size());
		Assertions.assertEquals(TOTAL_HITS, results4.getTotalHits().intValue());

		// now lets check pagination, for last page we should get less results
		pgCrit.setPageNumber((long) (TOTAL_HITS / IPagination.DEFAULT_RESULTS_PERPAGE));
		ISearchResults<AuditTrailSearchResult> results7 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(TOTAL_HITS % IPagination.DEFAULT_RESULTS_PERPAGE,
				results7.getResults().size());
		
		pgCrit.setPageNumber(0L);
		cfg.clear();
		cfg.setOid("SD6997"); // should be unique look up
		final int EXPECTED_HIT_COUNT = 2;
		ISearchResults<AuditTrailSearchResult> results5 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(EXPECTED_HIT_COUNT, results5.getResults().size());
		Assertions.assertEquals(EXPECTED_HIT_COUNT, results5.getTotalHits().intValue());

		cfg.setOid("SD699"); // should be no hits
		final int EXPECTED_HIT_COUNT2 = 0;
		ISearchResults<AuditTrailSearchResult> results6 = searcher.search(pgCrit, cfg);
		Assertions.assertEquals(EXPECTED_HIT_COUNT2, results6.getResults().size());
		

	}
	
	@Test
	 void searchReturnsTimeStampsInISO601UTC() {
		searcher.setLogFolder(new File("src/test/resources/TestResources/auditLogSearch"));
		searcher.setLogFilePrefix("RSLogs");
		AuditTrailSearchElement cfg = new AuditTrailSearchElement();
		ISearchResults<AuditTrailSearchResult> results4 = searcher.search(pgCrit, cfg);
		Assertions.assertTrue(results4.getResults().stream().allMatch(utcTimeStamp()));
	}

	private Predicate<? super AuditTrailSearchResult> utcTimeStamp() {
		return result->JacksonUtil.toJson(result).contains("Z");
	}

}
