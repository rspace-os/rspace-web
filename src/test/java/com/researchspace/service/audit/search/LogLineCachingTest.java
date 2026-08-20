package com.researchspace.service.audit.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.researchspace.model.audittrail.spring.SpringConfig;

@ContextConfiguration(classes = SpringConfig.class)
public class LogLineCachingTest extends AbstractJUnit4SpringContextTests {

	private @Autowired LogLineContentProvider logLineContentProvider;
	File logFileToCache = new File("src/test/resources/TestResources/sampleLogs/RSLogs.txt.1");
	File logFileNotToCache = new File("src/test/resources/TestResources/sampleLogs/RSLogs.txt");

	@Test
	public void testCaching() throws IOException, ParseException {

		List<LogLine> lines = logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(),
				logFileToCache);
    assertSame(lines, logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(),
        logFileToCache));
		
		// just mark to delete cache
		logLineContentProvider.removeFiles(Collections.emptyList());
    assertNotSame(lines, logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(),
        logFileToCache));

	}
	
	@Test
	public void testCurrFileNeverCached() throws IOException, ParseException {

		List<LogLine> lines = logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(),
				logFileNotToCache);
    assertNotSame(lines, logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(),
        logFileToCache));

	}

}
