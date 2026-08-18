package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

/**
 * Validates an incoming {@link ApiInventoryLink} payload independent of database state. Checks
 * relationType against the DataCite vocabulary, syntactic GlobalID parse, that the target prefix is
 * a supported link target (Inventory items including sample and instrument templates, or ELN
 * documents, notebooks and gallery files), and forbids self-links. Existence and read-permission
 * checks live in the manager layer, where the acting user is available.
 */
public class InventoryLinkValidator {

  /**
   * True when the prefix is a kind that links may target (shared with the manager's write-path
   * validation).
   */
  public static boolean isAllowedTargetPrefix(GlobalIdPrefix prefix) {
    return prefix != null && ALLOWED_TARGET_PREFIXES.contains(prefix);
  }

  /** True when target points at the same record as sourceGlobalId (version suffixes ignored). */
  public static boolean isSelfLink(GlobalIdentifier target, String sourceGlobalId) {
    if (StringUtils.isBlank(sourceGlobalId) || target == null) {
      return false;
    }
    try {
      GlobalIdentifier source = new GlobalIdentifier(sourceGlobalId);
      return source.getPrefix() == target.getPrefix() && source.getDbId().equals(target.getDbId());
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static final Set<GlobalIdPrefix> ALLOWED_TARGET_PREFIXES =
      EnumSet.of(
          GlobalIdPrefix.SA,
          GlobalIdPrefix.SS,
          GlobalIdPrefix.IC,
          GlobalIdPrefix.IN,
          GlobalIdPrefix.IT,
          GlobalIdPrefix.NT,
          GlobalIdPrefix.SD,
          GlobalIdPrefix.NB,
          GlobalIdPrefix.GL);

  /**
   * Validates the supplied link. Errors are rejected as {@code relationType} / {@code
   * targetGlobalId} field codes relative to whatever nested path the passed {@link Errors} object
   * is currently positioned at. This validator does not push a {@code link} path itself: callers
   * that want the errors to surface under {@code link.*} must {@code pushNestedPath("link")} on the
   * Errors object before calling (as {@code ApiExtraFieldsHelper} does). {@code sourceGlobalId}
   * (the GlobalID of the parent item the link is being attached to) is used to detect self-links.
   */
  public void validate(ApiInventoryLink link, String sourceGlobalId, Errors errors) {
    if (link == null) {
      return;
    }
    validateRelationType(link, errors);
    validateTarget(link, sourceGlobalId, errors);
  }

  private void validateRelationType(ApiInventoryLink link, Errors errors) {
    if (!DataCiteRelationType.isValid(link.getRelationType())) {
      errors.rejectValue(
          "relationType",
          "errors.inventory.field.linkRelationTypeInvalid",
          new Object[] {link.getRelationType()},
          null);
    }
  }

  private void validateTarget(ApiInventoryLink link, String sourceGlobalId, Errors errors) {
    String target = link.getTargetGlobalId();
    if (StringUtils.isBlank(target)) {
      errors.rejectValue(
          "targetGlobalId",
          "errors.inventory.field.linkTargetNotFound",
          new Object[] {target},
          null);
      return;
    }
    GlobalIdentifier parsed;
    try {
      parsed = new GlobalIdentifier(target);
    } catch (IllegalArgumentException ex) {
      errors.rejectValue(
          "targetGlobalId",
          "errors.inventory.field.linkTargetNotFound",
          new Object[] {target},
          null);
      return;
    }
    if (!ALLOWED_TARGET_PREFIXES.contains(parsed.getPrefix())) {
      errors.rejectValue(
          "targetGlobalId",
          "errors.inventory.field.linkTargetKindUnsupported",
          new Object[] {parsed.getPrefix().name()},
          null);
      return;
    }
    if (isSelfLink(parsed, sourceGlobalId)) {
      errors.rejectValue(
          "targetGlobalId",
          "errors.inventory.field.link.selfLinkForbidden",
          new Object[] {target},
          null);
    }
  }
}
