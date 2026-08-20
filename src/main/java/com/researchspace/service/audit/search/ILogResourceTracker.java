package com.researchspace.service.audit.search;

import java.io.File;
import java.util.Collection;

public interface ILogResourceTracker {
	
	Collection<File> filter(Collection<File> allLogs, AuditTrailSearchElement searchConfig);
	

}
