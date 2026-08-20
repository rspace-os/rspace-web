package com.researchspace.service.audit.search;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogLineParser {
	
	static final String LOG_TIME_STAMPPATTERN = "([^\\-]+)";
	static final String ALLOWED_USERNAME_CHARS = "[A-Za-z0-9@\\.\\->]{1,}";
	static final Pattern AUDIT_LOG_LINE = Pattern.compile("^" 
			+ LOG_TIME_STAMPPATTERN // timestamp
			+ "\\- domain:(\\w+)" // domain
			+ "\\s+action:(\\w+)" // action
			+ "\\s+\\[(.+)\\]" // arbitrary JSON data
			+ "\\s+("+ALLOWED_USERNAME_CHARS+")" // username
			+ "\\((.+)\\)" // full name
			+ "\\s*(description:\\[(.+)\\])?$"); // optional description

		
		File toParse = null;
		AuditTrailSearchElement searchConfig = null;
		
		List<LogLine> parseAll () throws IOException, ParseException{
			List<LogLine> allLines = new ArrayList<>();
			SimpleDateFormat logTimeStampFormat = new SimpleDateFormat(BasicLogQuerySearcher.DATE_FORMAT);
			List<String> lines = FileUtils.readLines(toParse, "UTF-8");
			for (String line: lines) {
				LogLine logline = parseLine(line, logTimeStampFormat);
				if (logline == null) {
					continue;
				}
				allLines.add(logline);
			}
			return allLines;
		}
			
		LogLine parseLine(String line, SimpleDateFormat logTimeStampFormat)
				throws ParseException {
			Matcher m = AUDIT_LOG_LINE.matcher(line);
			if (m.find()) {
				LogLine logline = new LogLine();
				String date = m.group(1);
				logline.date = logTimeStampFormat.parse(date);
				logline.domain = AuditDomain.valueOf(m.group(2));
				logline.action = AuditAction.valueOf(sanitizeAuditAction(m.group(3)));
				logline.data = m.group(4);
				logline.username = m.group(5);
				logline.fullname = m.group(6);
				logline.description = m.group(8);
				return logline;
			} else {
				return null;
			}
		}

	/***
	 * This his needed since we have renamed the deprecated COPY action
	 * to DUPLICATE
	 *
   */
	private String sanitizeAuditAction(String actionFromlog) {
		final String OLD_ACTION = "COPY";
		final String NEW_ACTION = "DUPLICATE";
		return OLD_ACTION.equalsIgnoreCase(actionFromlog) ? NEW_ACTION : actionFromlog;
	}
}
