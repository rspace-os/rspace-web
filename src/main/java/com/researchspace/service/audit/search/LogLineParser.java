package com.researchspace.service.audit.search;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogLineParser {

  File toParse = null;
  AuditTrailSearchElement searchConfig = null;

  List<LogLine> parseAll() throws IOException, ParseException {
    List<LogLine> allLines = new ArrayList<>();
    SimpleDateFormat logTimeStampFormat = new SimpleDateFormat(BasicLogQuerySearcher.DATE_FORMAT);
    List<String> lines = FileUtils.readLines(toParse, "UTF-8");
    for (String line : lines) {
      LogLine logline = parseLine(line, logTimeStampFormat);
      if (logline == null) {
        continue;
      }
      allLines.add(logline);
    }
    return allLines;
  }

  LogLine parseLine(String line, SimpleDateFormat logTimeStampFormat) throws ParseException {
    var fields = AuditLogLineFormat.parse(line, malformed -> {});
    if (fields.isEmpty()) {
      return null;
    }
    var parsed = fields.get();
    LogLine logline = new LogLine();
    logline.date = logTimeStampFormat.parse(parsed.timestamp());
    logline.domain = AuditDomain.valueOf(parsed.domain());
    logline.action = AuditAction.valueOf(parsed.action());
    logline.data = parsed.data();
    logline.username = parsed.username();
    logline.fullname = parsed.fullName();
    logline.description = parsed.description();
    return logline;
  }
}
