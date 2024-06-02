package com.researchspace.service.inventory.csvexport;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CsvExportCommentGenerator {

  // from rs.properties
  @Value("${rsversion}")
  private String rsversion;

  private @Autowired IPropertyHolder properties;

  protected enum ExportedCommentProperty {
    EXPORTED_CONTENT("Exported content"),
    EXPORTED_BY("Exported by"),
    EXPORTED_AT("Exported at"),
    EXPORT_MODE("Export mode"),
    EXPORT_SCOPE("Export scope"),
    RSPACE_URL("RSpace URL"),
    RSPACE_VERSION("RSpace version");

    @Getter private final String csvPropertyName;

    private ExportedCommentProperty(String csvPropertyName) {
      this.csvPropertyName = csvPropertyName;
    }
  }

  public Map<ExportedCommentProperty, String> getPropertiesForCsvCommentFragment(
      String exportedContentString, ExportScope exportScope, CsvExportMode exportMode, User user)
      throws IOException {

    Map<ExportedCommentProperty, String> result = new LinkedHashMap<>();
    result.put(ExportedCommentProperty.EXPORTED_CONTENT, exportedContentString);
    result.put(ExportedCommentProperty.EXPORTED_BY, user.getFullName());
    result.put(
        ExportedCommentProperty.EXPORTED_AT,
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    result.put(ExportedCommentProperty.RSPACE_URL, properties.getServerUrl());
    result.put(ExportedCommentProperty.RSPACE_VERSION, rsversion);
    result.put(ExportedCommentProperty.EXPORT_MODE, exportMode.toString());
    result.put(ExportedCommentProperty.EXPORT_SCOPE, exportScope.toString());
    return result;
  }
}
