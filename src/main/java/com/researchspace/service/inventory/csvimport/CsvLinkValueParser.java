package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InventoryLinkValidator;
import com.researchspace.service.inventory.InventoryUrls;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import java.util.Optional;
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
  @Autowired LinkTargetResolver linkTargetResolver;
  @Autowired LinkTargetSnapshotResolver snapshotResolver;

  /**
   * @param importer the importing user. A pinned version is checked only when they can read the
   *     target: an item they cannot read, or that does not exist, still imports as a dangling link,
   *     so the import never reveals whether it, or a version of it, exists (ADR-0002, RSDEV-1530).
   * @return the link described by the cell, flagged lenient so a target that no longer exists on
   *     this server still imports as a dangling link
   * @throws IllegalArgumentException with a user-facing message when the cell is not a link, or
   *     pins a version that a target the importer can read does not have
   */
  public ApiInventoryLink parse(String cell, User importer) {
    ApiInventoryLink link = tryParse(cell);
    if (link == null) {
      throw new IllegalArgumentException(
          messages.getMessage("errors.inventory.import.linkValueInvalid", new Object[] {cell}));
    }
    if (link.getVersionPin() != null) {
      GlobalIdentifier target = new GlobalIdentifier(link.getTargetGlobalId());
      // readable first, then the version; both lookups answer false or null rather than throw,
      // so a refused row never marks the import's transaction rollback-only
      if (linkTargetResolver.targetExistsAndIsReadable(target, importer)
          && snapshotResolver.resolveRevisionForVersion(
                  target.getPrefix(), target.getDbId(), link.getVersionPin())
              == null) {
        throw new IllegalArgumentException(
            messages.getMessage(
                "errors.inventory.field.linkTargetVersionNotFound",
                new Object[] {link.getTargetGlobalId(), link.getVersionPin()}));
      }
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
    // the same rule the PIDINST import uses to decide an address is this server's own page; empty
    // without a server URL, so a relative-looking cell never names a local record
    Optional<String> globalId =
        InventoryUrls.globalIdOfOwnPage(m.group(2), properties.getServerUrl());
    if (globalId.isEmpty()) {
      return null;
    }
    GlobalIdentifier gid;
    try {
      gid = new GlobalIdentifier(globalId.get());
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
