package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InventoryLinkValidator;
import com.researchspace.service.inventory.InventoryUrls;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Parses the CSV cell the exporters write for a link field: {@code "<RelationType>
 * <serverUrl>/globalId/<GID>[vN]"}. Only URLs on this server are accepted (RSDEV-1354), so a Global
 * ID from another RSpace instance is never silently attached to the wrong local record.
 */
@Component
public class CsvLinkValueParser {

  private static final Pattern CELL = Pattern.compile("^(\\S+)\\s+(\\S+)$");

  @Autowired IPropertyHolder properties;
  @Autowired MessageSourceUtils messages;

  /**
   * @return the link described by the cell, flagged lenient so a target that no longer exists on
   *     this server still imports as a dangling link
   * @throws IllegalArgumentException with a user-facing message when the cell is not a link
   */
  public ApiInventoryLink parse(String cell) {
    ApiInventoryLink link = tryParse(cell);
    if (link == null) {
      throw new IllegalArgumentException(
          messages.getMessage("errors.inventory.import.linkValueInvalid", new Object[] {cell}));
    }
    return link;
  }

  public boolean isParseable(String cell) {
    return tryParse(cell) != null;
  }

  private ApiInventoryLink tryParse(String cell) {
    if (cell == null) {
      return null;
    }
    Matcher m = CELL.matcher(cell.trim());
    if (!m.matches() || !DataCiteRelationType.isValid(m.group(1))) {
      return null;
    }
    String url = m.group(2);
    String prefix = InventoryUrls.globalIdPagePrefix(properties.getServerUrl()).orElse(null);
    // no server URL configured: nothing can be shown to name a local record, so accept nothing
    // rather than letting the prefix collapse to "/globalId/" and match a relative-looking cell
    if (prefix == null || !url.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }
    GlobalIdentifier gid;
    try {
      gid = new GlobalIdentifier(url.substring(prefix.length()));
    } catch (IllegalArgumentException ex) {
      return null;
    }
    if (!InventoryLinkValidator.isAllowedTargetPrefix(gid.getPrefix())) {
      return null;
    }
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(m.group(1));
    link.setTargetGlobalId(new GlobalIdentifier(gid.getPrefix(), gid.getDbId()).getIdString());
    link.setVersionPin(gid.hasVersionId() ? gid.getVersionId() : null);
    link.setSkipTargetCheck(true);
    return link;
  }
}
