package com.researchspace.service.audit.search;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;

public interface LogLineContentProvider {
	
	void  removeFiles(Collection<File> allLogs);
	
	 List<LogLine> getLinesFromFile(AuditTrailSearchElement searchConfig, File logFile) throws IOException, ParseException;

}
