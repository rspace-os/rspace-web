package com.researchspace.service.audit.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.researchspace.core.util.DateRange;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides some sort of optimisation of log file searching by caching the start/end dates of each
 * log file. Searches restricted by date can then ignore certain log files if they lie outside the
 * date range of the log files.
 * <p>
 * This assumes that log files are sequentially ordered using the nomenclature LogFile.txt.1,
 * logfile.txt.2 etc. the file with the highest suffix is the 'current' log file that is still being
 * appended to.
 */
@Slf4j
public class LogFileTracker implements ILogResourceTracker {

	private Map<String, DateRange> logDateRanges = new ConcurrentHashMap<>();

	private String currentFile;
	private AtomicInteger currentLogFileCount = new AtomicInteger();
	private LogLineContentProvider lineContentProvider;

	public LogFileTracker(@Autowired LogLineContentProvider lineContentProvider) {
		super();
		this.lineContentProvider = lineContentProvider;
	}

	private boolean isCurrentFile(String file) {
		return file.equals(currentFile);
	}

	/**
	 * Given a list of log files, will return only those that match the restriction set by the
	 * {@link AuditTrailSearchElement}, that need to be parsed.
	 * 
	 * @return A filtered collection; possibly empty but not null.
	 */
	public Collection<File> filter(Collection<File> allLogs, AuditTrailSearchElement searchConfig) {
		List<File> filtered = new ArrayList<>();
		updateCurrentFile(allLogs);
		for (File file : allLogs) {
			if (logDateRanges.get(file.getName()) != null && !isCurrentFile(file.getName())) {
				if (searchConfig.getDateRange().overlapsWith(logDateRanges.get(file.getName()))) {
					log.info("Adding file {} to include in search filtered", file.getName());
					filtered.add(file);
				}
			}
			if (isCurrentFile(file.getName()) && !filtered.contains(file)) {
				log.info("Adding current file  {} to include in search filtered", file.getName());
				filtered.add(file);
			}
		}
		return filtered;
	}

	static class LogFileComparator implements Comparator<File> , Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = -1341127247466404788L;

		@Override
		public int compare(File arg0, File arg1) {
			String f1name = arg0.getName();
			String f2name = arg1.getName();
			String f1suffix = f1name.substring(f1name.lastIndexOf(".") + 1);
			String f2suffix = f2name.substring(f2name.lastIndexOf(".") + 1);
			// make sure earliest log file is alwasys hte first
			if (!StringUtils.isNumeric(f1suffix)) {
				return -1;
			} else if (!StringUtils.isNumeric(f2suffix)) {
				return 1;
			} else {
				Integer suffix1 = Integer.parseInt(f1name.substring(f1name.lastIndexOf(".") + 1));
				Integer suffix2 = Integer.parseInt(f2name.substring(f2name.lastIndexOf(".") + 1));
				return suffix1.compareTo(suffix2);
			}
		}
	}

	private void updateCurrentFile(Collection<File> allLogs) {
		if(allLogs.size() != currentLogFileCount.get() ){
			log.info("log file count has changed from {} to {}, rollover has occurred,"
					+ "  regenerating logfile start-end times", currentLogFileCount.get(), allLogs.size());
			lineContentProvider.removeFiles(allLogs);
			currentLogFileCount.set(allLogs.size());
			logDateRanges.clear();
		}
    List<File> toSort = new ArrayList<>(allLogs);
		toSort.sort(new LogFileComparator());
		for (int i = 0; i < toSort.size(); i++) {
			File f = toSort.get(i);
			// if isn't current, but we've not cached dates yet, then do so.
			if (i >0 && !logDateRanges.containsKey(f.getName())) {
				try {
					Date start = getStartDate(f);
					log.info("Start date of logfile {} is {}", f.getName(), start);
					Date end = getEndDate(f);
					log.info("end date of logfile {} is {}, caching range.", f.getName(), end);
					DateRange dr = new DateRange(start, end);
					logDateRanges.put(f.getName(), dr);
				} catch (IOException | ParseException e) {
					log.warn("parsing problem", e);
				}
      } else if (i == 0) {
				log.info("Setting current logfile as {}", currentFile);
				this.currentFile = f.getName();
			}
		}

	}

	

	private Date getEndDate(File f) throws IOException, ParseException {
		try (ReversedLinesFileReader reader = ReversedLinesFileReader.builder().setFile(f).get()) {
			String line = reader.readLine();
			SimpleDateFormat logTimeStampFormat = new SimpleDateFormat(BasicLogQuerySearcher.DATE_FORMAT);
			Matcher m = timestamp.matcher(line);

			if (m.find()) {
				return logTimeStampFormat.parse(m.group(1));
			} else {
				return null;
			}
		}
	}

	private Pattern timestamp = Pattern.compile("^" + LogLineParser.LOG_TIME_STAMPPATTERN);

	private Date getStartDate(File f) throws IOException, ParseException {
		SimpleDateFormat logTimeStampFormat = new SimpleDateFormat(
				BasicLogQuerySearcher.DATE_FORMAT);
	
		try (InputStream in = new FileInputStream(f);
			 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
				BufferedReader br = new BufferedReader(reader)) {
			
			// br = new BufferedReader(new FileReader(f));
			String line = br.readLine();
			Matcher m = timestamp.matcher(line);
			if (m.find()) {
				return logTimeStampFormat.parse(m.group(1));
			} else {
				return null;
			}
		} 
	}

}
