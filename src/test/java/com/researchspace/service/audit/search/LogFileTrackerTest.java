package com.researchspace.service.audit.search;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;

import com.researchspace.core.util.DateRange;
@EnableRuleMigrationSupport
public class LogFileTrackerTest {
	LogFileTracker tracker;
	File logFolder;
	final int TOTAL_LOG_FILES = 5;
	SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@BeforeEach
	public void setUp() {
		tracker = new LogFileTracker(new LogLineContentProviderImpl());
		// 3 years of logs . 2010,2011,2014
		this.logFolder = new File("src/test/resources/TestResources/sampleLogs");
	}

	@AfterEach
	public void tearDown() {
	}

	@Test
	public void testFilter() throws ParseException {
		Collection<File> logs = FileUtils.listFiles(logFolder, new PrefixFileFilter("RSLogs"), null);
		AuditTrailSearchElement element = new AuditTrailSearchElement();
		// no restriction, can't exclude any files
		element.setDateRange(new DateRange(null, Long.MAX_VALUE));
		Assertions.assertEquals(TOTAL_LOG_FILES, tracker.filter(logs, element).size());

		// now lets set a range before 2010
		Date start = sdf.parse("21/12/1975");
		Date end = sdf.parse("21/12/1976");
		element.setDateRange(new DateRange(start, end));
		// will always look at current log file
		Assertions.assertEquals(1, tracker.filter(logs, element).size());

		start = sdf.parse("12/05/2016");
		end = sdf.parse("12/05/2017");
		// current + .1
		element.setDateRange(new DateRange(start, end));
		Collection<File> filtered = tracker.filter(logs, element);
		Assertions.assertEquals(2, filtered.size());
		Assertions.assertTrue(filtered.stream().anyMatch(f -> f.getName().equals("RSLogs.txt.1")));

		// now lets set a range that is totally within an old log file
		 start = sdf.parse("13/05/2014");
		 end = sdf.parse("17/12/2014");
		element.setDateRange(new DateRange(start, end));
		 filtered = tracker.filter(logs, element);
		// will always look at current log file + RSLOgs2
		Assertions.assertEquals(2, filtered.size());
		Assertions.assertTrue(filtered.stream().anyMatch(f -> f.getName().equals("RSLogs.txt.2")));
		

		// set a range too far in the future....
		start = sdf.parse("01/01/2021");
		end = sdf.parse("31/12/2021");
		// current file only
		element.setDateRange(new DateRange(start, end));
		filtered = tracker.filter(logs, element);
		Assertions.assertEquals(1, filtered.size());
		Set<String> fileNames = filtered.stream().map(File::getName).collect(Collectors.toSet());
		Assertions.assertTrue(fileNames.contains("RSLogs.txt"));

		// now try completely unconstrained search should return all files
		element.clear();
		Assertions.assertEquals(TOTAL_LOG_FILES, tracker.filter(logs, element).size());
	}

	private Set<String> searchBetween2Dates(Collection<File> logs) throws ParseException {
		AuditTrailSearchElement element = new AuditTrailSearchElement();
		Date start;
		Date end;
		Collection<File> filtered;
		// now lets span 2 log files
		start = sdf.parse("01/01/2011");
		end = sdf.parse("31/12/2014");
		// current + 2 file
		element.setDateRange(new DateRange(start, end));
		filtered = tracker.filter(logs, element);
		Assertions.assertEquals(3, tracker.filter(logs, element).size());
    return filtered.stream().map(File::getName).collect(Collectors.toSet());
	}

	@Test
	public void testFileRolloverDetected() throws ParseException, IOException {
		File tempDir = folder.getRoot();
		FileUtils.copyDirectory(logFolder, tempDir, null);
		Assertions.assertEquals(5, getAllFilesInFolder(tempDir).size());
		Set<String> fileNames = searchBetween2Dates(getAllFilesInFolder(tempDir));

		Assertions.assertTrue(fileNames.contains("RSLogs.txt.3"));
		Assertions.assertTrue(fileNames.contains("RSLogs.txt.2"));
		Assertions.assertTrue(fileNames.contains("RSLogs.txt"));
		rolloverLogFiles(tempDir);
		fileNames = searchBetween2Dates(getAllFilesInFolder(tempDir));
		Assertions.assertTrue(fileNames.contains("RSLogs.txt.4"));
		Assertions.assertTrue(fileNames.contains("RSLogs.txt.3"));
		Assertions.assertTrue(fileNames.contains("RSLogs.txt"));
	}

	private Collection<File> getAllFilesInFolder(File tempDir) {
		return FileUtils.listFiles(tempDir, FileFilterUtils.trueFileFilter(), null);
	}

	private void rolloverLogFiles(File tempDir) throws IOException {
		File currFile = new File(tempDir, "RSLogs.txt");
		// now rename each of the 4 log files:
		IntStream.of(4, 3, 2, 1).forEach(i -> {
			File f = new File(tempDir, "RSLogs.txt." + i);
			Assertions.assertTrue(f.renameTo(new File(tempDir, "RSLogs.txt." + (i + 1))));
		});
		Assertions.assertTrue(currFile.renameTo(new File(tempDir, "RSLogs.txt.1")));
		File newCurLog = new File(tempDir, "RSLogs.txt");
		FileUtils.write(newCurLog,
				"12 May 2018 16:10:06,627 - [/app/workspace/7141] from 127.0.0.1 with args: [] made by: [user2b]",
				"UTF-8");
		Assertions.assertEquals(6, getAllFilesInFolder(tempDir).size());
	}

}
