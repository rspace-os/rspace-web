package com.researchspace.service.audit.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.HistoricData;

/**
 * Simple parse of log files for testing /dev, <b> Not </b> for production
 */
public class BasicLogQuerySearcher implements IAuditFileSearch {
	private static final String OPERATE_AS_DELIMITER = "->";
	/**
	 * Date format used by log4j logger
	 */
	static final String DATE_FORMAT = "dd MMM yyyy HH:mm:ss,SSS";
	private File logFolder = null;

	private String logFilePrefix = "RSLogs";// default

	private ILogResourceTracker tracker;
	@Value("${logging.dir}")
	private String loggingDir;

	private LogLineContentProvider lineContentProvider;
	public BasicLogQuerySearcher(@Autowired ILogResourceTracker logFileTracker, @Autowired LogLineContentProvider lineContentProvider) {
		this.tracker = logFileTracker;
		this.lineContentProvider = lineContentProvider;
	}

	public void setLogFilePrefix(String logFilePrefix) {
		if (StringUtils.isBlank(logFilePrefix)) {
			throw new IllegalArgumentException("logFilePrefix cannot be empty");
		}
		this.logFilePrefix = logFilePrefix;
	}

	public void setLogFolder(File logFolder) {
		if (logFolder == null || !logFolder.isDirectory()) {
			throw new IllegalArgumentException("logfolder [" + logFolder + "] is not a folder.");
		}
		this.logFolder = logFolder;
	}

	private void initLogFolder() {
		if (logFolder == null) {
			if (StringUtils.isEmpty(loggingDir)) {
				loggingDir = ".";
			}
			File file = new File(loggingDir);
			setLogFolder(file);
		}
	}

	private org.slf4j.Logger log = LoggerFactory.getLogger(BasicLogQuerySearcher.class);

	private boolean idMatches(AuditTrailSearchElement searchConfig, LogLine logline) {
		// need quotes to specifiy delimiters. E.g., to make sure SD10 doesn't
		// match SD101
		return logline.data.contains("\"" + searchConfig.getOid() + "\"");
	}

	@SuppressWarnings("rawtypes")
	@Override
	public ISearchResults<AuditTrailSearchResult> search(IPagination pgCrit,
			AuditTrailSearchElement searchConfig) {
		if (pgCrit == null || searchConfig == null) {
			throw new IllegalArgumentException(
					"Both arguments cannot be null, but were: [" + pgCrit + searchConfig + "]");
		}
		StopWatch sw = new StopWatch();
		sw.start();

		initLogFolder();
		if (logFolder == null) {
			log.error("Could not find log file folder");
			return createEmptyResults(pgCrit);

		}
		Collection<File> logs = getLogFiles();
		logs = getTracker().filter(logs, searchConfig);
		List<LogLine> hits = new ArrayList<>();

		// first of all iterate over logs and identify hits
		try {
			for (File logFile : logs) {
				List<LogLine> linesInFile = lineContentProvider.getLinesFromFile(searchConfig, logFile);
				for (LogLine logline : linesInFile) {
					// if oid is set, and doesn't match, we don't need to test
					// the others.
					if (searchConfig.getOid() != null && !idMatches(searchConfig, logline)) {
						continue;
					} else if ((searchConfig.getUsernames().isEmpty()
							|| isUserNameMatch(searchConfig, logline))
							&& (searchConfig.getOid() == null || idMatches(searchConfig, logline))
							&& searchConfig.getActions().contains(logline.action)
							&& searchConfig.getDomains().contains(logline.domain)
							&& searchConfig.getDateRange().contains(logline.date)) {
						hits.add(logline);
					}
				}

			}
		} catch (IOException | ParseException e) {
			log.warn("parsing problem", e);
		}

    Comparator<LogLine> sorter = getComparator(pgCrit, hits);
		// sort results if specified
		if (sorter != null) {
			hits.sort(sorter);
		}
		// now create paginated results
		sw.stop();
    log.info("Parsed in {}", sw.getTime());
		if (hits.isEmpty()) {
			return createEmptyResults(pgCrit);
		} else {
			List<AuditTrailSearchResult> results = new ArrayList<>();
			int firstResult = pgCrit.getFirstResultIndex();
			int lastResult = pgCrit.getFirstResultIndex() + pgCrit.getResultsPerPage();
			int count = 0;
			for (LogLine hit : hits) {
				HistoricData hd = new HistoricData(hit.domain, hit.action, hit.fullname, AuditData.fromJson(hit.data),
						hit.username);
				if (hit.description != null) {
					hd.setDescription(hit.description);
				}
				hd.setTimestamp(hit.date);
				AuditTrailSearchResult result = new AuditTrailSearchResult(hd, hit.date.getTime());
				if (count >= firstResult && count < lastResult) {
					results.add(result);
				}
				count++;
				if (count == lastResult) {
					break;
				}
			}
			return new SearchResultsImpl<>(results, pgCrit, hits.size());
		}
	}

	private boolean isUserNameMatch(AuditTrailSearchElement searchConfig, LogLine logline) {
		boolean rc = false;
		if (searchConfig.getUsernames().contains(logline.username)) {
			rc = true;
		} else if (logline.username.contains(OPERATE_AS_DELIMITER)){
			String [] operateAs = logline.username.split(OPERATE_AS_DELIMITER);
			rc = searchConfig.getUsernames().contains(operateAs[0]) || searchConfig.getUsernames().contains(operateAs[1]);
		}
		return rc;
	}

	// cache files except current file with no numeric suffix
	

	private Collection<File> getLogFiles() {
		IOFileFilter fileFilter = new RegexFileFilter("^" + logFilePrefix + ".*$");
    return FileUtils.listFiles(logFolder, fileFilter, null);
	}

	private synchronized ILogResourceTracker getTracker() {
		return tracker;
	}
	
	ISearchResults<AuditTrailSearchResult> createEmptyResults(IPagination<?>  pgCrit) {
		return new SearchResultsImpl<>(Collections.emptyList(), pgCrit, 0);
	}

	Comparator<LogLine> getComparator(IPagination<?> pgcrit, List<LogLine> hits) {
		if ("username".equals(pgcrit.getOrderBy())) {
			return new LogLineFullnameComparator(pgcrit.getSortOrder());
		} else if ("action".equals(pgcrit.getOrderBy())) {
			return new LogLineActionComparator(pgcrit.getSortOrder());
		} else if ("date".equals(pgcrit.getOrderBy())) {
			return new LogLineDateComparator(pgcrit.getSortOrder());
		} else {
			return null;
		}
	}

	static class LogLineFullnameComparator implements Comparator<LogLine>, Serializable {

		private static final long serialVersionUID = 1L;
		private SortOrder sortOrder;

		@Override
		public int compare(LogLine arg0, LogLine arg1) {
			int rc;
			
			if (SortOrder.ASC.equals(sortOrder)) {
				rc = arg0.fullname.toLowerCase().compareTo(arg1.fullname.toLowerCase());
			} else {
				rc = arg1.fullname.toLowerCase().compareTo(arg0.fullname.toLowerCase());
			}
			if (rc != 0) {
				return rc;
			} else {
				return arg1.date.compareTo(arg0.date);
			}
		}

		LogLineFullnameComparator(SortOrder sortOrder) {
			super();
			this.sortOrder = sortOrder;
		}
	}

	static class LogLineDateComparator implements Comparator<LogLine>, Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private SortOrder sortOrder;

		@Override
		public int compare(LogLine arg0, LogLine arg1) {

			if (SortOrder.ASC.equals(sortOrder)) {
				return arg0.date.compareTo(arg1.date);
			} else {
				return arg1.date.compareTo(arg0.date);
			}

		}

		LogLineDateComparator(SortOrder sortOrder) {
			super();
			this.sortOrder = sortOrder;
		}
	}

	static class LogLineActionComparator implements Comparator<LogLine>, Serializable {

		private static final long serialVersionUID = 1L;
		private SortOrder sortOrder;

		@Override
		public int compare(LogLine arg0, LogLine arg1) {
			int rc;
			if (SortOrder.ASC.equals(sortOrder)) {
				rc = arg0.action.compareTo(arg1.action);
			} else {
				rc = arg1.action.compareTo(arg0.action);
			}
			if (rc != 0) {
				return rc;
			} else {
				return arg1.date.compareTo(arg0.date);
			}
		}

		LogLineActionComparator(SortOrder sortOrder) {
			super();
			this.sortOrder = sortOrder;
		}
	}

}
