package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.core.util.IPagination;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;

public class LogLineParserTest {

	// complex line
	String TEST1 = "19 May 2014 16:06:27,230 - domain:RECORD action:RESTORE [{\"data\":{\"id\":6985,\"restoreType\":\"REVISION\",\"name\":\"Editable2\"}}] user1a(Bob Smith)description:[some data]";

	// description with '[' characters
	String TEST2 = "22 Aug 2014 08:27:19,933 - domain:GROUP action:WRITE [{\"data\":{\"id\":6985,\"restoreType\":\"REVISION\",\"name\":\"Editable2\"}}] sysadmin1(System Admin)description:[Removed [user9].]";

	// no description
	String TEST3 = "22 Aug 2014 08:28:18,010 - domain:AUDIT action:SEARCH [{\"data\":{}}] sysadmin1(System Admin)";

	String TEST_COPY_ACTION = "16 May 2014 12:52:13,261 - domain:RECORD action:DUPLICATE [{\"data\":{\"to\":{\"data\":{\"id\":6978,\"name\":\"Editable2_Copy_Copy\"}},\"from\":{\"data\":{\"id\":6963,\"name\":\"Editable2_Copy\"}}}}] userNik(Nik Ferrante)";

	// operate as username
	String OPERATE_AS = "22 Aug 2019 08:28:18,010 - domain:AUDIT action:SEARCH [{\"data\":{}}] sysadmin1->richard2(System Admin -> Richard User)";

	// shared
	String SHARED = "15 May 2015 12:18:16,447 - domain:RECORD action:SHARE [{\"data\":{\"name\":\"e1\",\"userVersion\":{\"version\":\"3\"},\"id\":\"SD15186\",\"type\":\"NORMAL\",\"sharing\":[{\"userId\":null,\"groupid\":3342336,\"email\":null,\"externalGroupId\":null,\"operation\":\"read\"}]}}] richard(Richard Adams2)";

	LogLineParser parser;
	static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			BasicLogQuerySearcher.DATE_FORMAT);
	static final SimpleDateFormat inputFormat = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	IPagination<AuditTrailSearchResult> pgCrit = BasicPaginationCriteria
			.createDefaultForClass(AuditTrailSearchResult.class);

	@BeforeEach
	public void setUp() {
		parser = new LogLineParser();
		pgCrit = BasicPaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class);
	}

	@AfterEach
	public void tearDown() {
	}

	@Test
	public void testSearch() throws ParseException {
		LogLine log = parser.parseLine(TEST1, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.RESTORE, log.getAction());
		Assertions.assertEquals(AuditDomain.RECORD, log.getDomain());
		Assertions.assertEquals("user1a", log.getUsername());
		
	}
	
	@Test
	public void testSearchForShared() throws ParseException {
		LogLine log = parser.parseLine(SHARED, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.SHARE, log.getAction());
		Assertions.assertEquals(AuditDomain.RECORD, log.getDomain());
		Assertions.assertEquals("richard", log.getUsername());
	}
	
	@Test
	public void testSearch2() throws ParseException {
		LogLine log = parser.parseLine(TEST2, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.WRITE, log.getAction());
		Assertions.assertEquals(AuditDomain.GROUP, log.getDomain());
	//	assertEquals("Removed [user9].", log.)
		Assertions.assertEquals("sysadmin1", log.getUsername());
	}
	@Test
	public void testOperateAs() throws ParseException {
		LogLine log = parser.parseLine(OPERATE_AS, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.SEARCH, log.getAction());
		Assertions.assertEquals(AuditDomain.AUDIT, log.getDomain());
		Assertions.assertNull(log.getDescription());
		Assertions.assertEquals("sysadmin1->richard2", log.getUsername());
	}
	
	@Test
	public void testSearch3() throws ParseException {
		LogLine log = parser.parseLine(TEST3, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.SEARCH, log.getAction());
		Assertions.assertEquals(AuditDomain.AUDIT, log.getDomain());
		Assertions.assertNull(log.getDescription());
		Assertions.assertEquals("sysadmin1", log.getUsername());
	}

	@Test
	public void successfullySearchWhenFindOldCopyAction() throws ParseException {
		LogLine log = parser.parseLine(TEST_COPY_ACTION, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.DUPLICATE, log.getAction());
		Assertions.assertEquals(AuditDomain.RECORD, log.getDomain());
		Assertions.assertEquals("userNik", log.getUsername());

	}
	@Test
	public void testOddUserNamesAreHandled_RSPAC_546 () throws ParseException{
		String RSPAC_546BUG = "22 Aug 2014 08:28:18,010 - domain:AUDIT action:SEARCH [{\"data\":{}}] sys@admin1(System Admin)";
		LogLine log = parser.parseLine(RSPAC_546BUG, dateFormat);
		Assertions.assertNotNull(log);
		Assertions.assertEquals(AuditAction.SEARCH, log.getAction());
		Assertions.assertEquals(AuditDomain.AUDIT, log.getDomain());
		Assertions.assertNull(log.getDescription());
		Assertions.assertEquals("sys@admin1", log.getUsername());
	}

}
