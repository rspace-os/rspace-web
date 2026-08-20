package com.researchspace.service.audit.search;

import java.io.File;

/**
 * Interface for file-based audit trail data
 */
public interface IAuditFileSearch extends IAuditTrailSearch {
	/**
	 * Sets the folder where log files are stored
	 * 
	 * @param logFolder
	 *            The top-level folder of log files
	 */
	void setLogFolder(File logFolder);

	/**
	 * Sets the log file prefix to identify log files in the folder - for use
	 * with Rolling log files log1.txt, log2.txt etc. where prefix would be
	 * 'log'.
	 *
   */
	void setLogFilePrefix(String logFilePrefix);

}
