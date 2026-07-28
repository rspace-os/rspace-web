package com.researchspace.service.audit.search;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogLineContentProviderImpl implements LogLineContentProvider {

	static final String cacheKeySpel = "getName()";

	static final String cacheArg = "#p1.";

	static final String cacheUnless = "getName() matches '.*\\.txt$'";
	
	public static final String AUDIT_FILES_CACHE = "com.researchspace.service.audit.files";

	@CacheEvict(cacheNames = AUDIT_FILES_CACHE, allEntries = true)
	public void removeFiles(Collection<File> allLogs) {
		log.info("EVicted all log files from cache");
	}

	@Cacheable(cacheNames = "com.researchspace.service.audit.files", key = cacheArg + cacheKeySpel, unless = cacheArg
			+ cacheUnless)
	public List<LogLine> getLinesFromFile(AuditTrailSearchElement searchConfig, File logFile)
			throws IOException, ParseException {
		log.info("Not cached - retrieving for file {}", logFile.getName());
		LogLineParser parser = new LogLineParser(logFile, searchConfig);
    return parser.parseAll();
	}

}
