package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

/**
 * Asserts that an Inventory entity has no duplicates among the names of its user-provided fields
 * (SampleField / InventoryEntityField + ExtraField). Catches:
 *
 * <ul>
 *   <li>two SampleFields with the same name on a Sample / SampleTemplate,
 *   <li>two ExtraFields with the same name on any record,
 *   <li>a SampleField and an ExtraField sharing a name on the same record (cross-collection).
 * </ul>
 *
 * Names are compared <strong>case-insensitively</strong> after trimming leading/trailing
 * whitespace. This mirrors {@code InventoryRecord.verifyFieldNameAllowed} in {@code
 * rspace-core-model}, which stores reserved names lowercase and lowercases the candidate before
 * comparing — both checks therefore use the same key. Null or blank names are skipped (they have
 * their own validation upstream: required-name checks).
 *
 * <p>This class does NOT enforce collisions with the entity's hardcoded displayed labels (e.g.
 * ExtraField named {@code "Type"} on a Container). That rule is owned by {@code
 * InventoryRecord.verifyFieldNameAllowed} in {@code rspace-core-model}, which throws {@code
 * IllegalArgumentException} for both reserved-name and displayed-label clashes.
 *
 * <p>For in-payload duplicates this class throws {@link ApiRuntimeException} with error code {@code
 * errors.inventory.field.duplicate.name} (mapped to HTTP 422 by the controller advice) at the
 * manager layer, or rejects via Spring's {@link Errors} at the validator layer (mapped to HTTP 400
 * by the controller advice's {@code BindException} handler). The user-facing message keeps the
 * caller's original casing of the duplicated name.
 */
public final class InventoryFieldNameUniquenessValidator {

  public static final String DUPLICATE_NAME_ERROR_CODE = "errors.inventory.field.duplicate.name";

  private InventoryFieldNameUniquenessValidator() {}

  /**
   * Post-mutation entity-side check. Walks the entity's active (non-deleted) SampleFields /
   * InventoryEntityFields and ExtraFields and rejects the SECOND occurrence of any case-insensitive
   * name match. Because {@code getActiveExtraFields()} excludes fields marked deleted, this method
   * naturally permits the "delete X then add X in the same request" idiom.
   */
  public static void assertNoDuplicateFieldNames(InventoryRecord record) {
    Set<String> seen = new HashSet<>();
    if (record instanceof Sample) {
      for (InventoryEntityField field : ((Sample) record).getActiveFields()) {
        rejectIfDuplicate(seen, field.getName());
      }
    } else if (record instanceof InstrumentEntity) {
      for (InventoryEntityField field : ((InstrumentEntity) record).getActiveFields()) {
        rejectIfDuplicate(seen, field.getName());
      }
    }
    for (ExtraField extraField : record.getActiveExtraFields()) {
      rejectIfDuplicate(seen, extraField.getName());
    }
  }

  /**
   * Request-payload sanity check, to be called BEFORE the API entries are applied to the entity.
   * Required because {@code InventoryRecord.addExtraField} silently dedups via {@code
   * extraFields.contains(toAdd)} (two fresh ExtraField instances created with the same name in the
   * same millisecond compare equal via their EditInfo), so a post-mutation check on the entity
   * cannot see the duplicate the user submitted.
   *
   * <p>Entries marked {@code deleteFieldRequest} are skipped, so deleting and re-adding a name in
   * the same request is allowed by this check (the post-mutation check enforces the same property).
   * Comparison is case-insensitive after trimming.
   *
   * @param record reserved for future use (label-collision is owned by core-model); currently
   *     unused but retained on the signature for symmetry with the manager-layer paths.
   * @param fields the incoming SampleField / InventoryEntityField list, or null. Entries marked
   *     {@code deleteFieldRequest} are skipped.
   * @param extraFields the incoming ExtraField list, or null.
   */
  public static void assertNoDuplicateFieldNamesInRequest(
      InventoryRecord record,
      List<ApiInventoryEntityField> fields,
      List<ApiExtraField> extraFields) {
    Set<String> seen = new HashSet<>();
    if (fields != null) {
      for (ApiInventoryEntityField field : fields) {
        if (field.isDeleteFieldRequest()) {
          continue;
        }
        rejectIfDuplicate(seen, field.getName());
      }
    }
    if (extraFields != null) {
      for (ApiExtraField extraField : extraFields) {
        if (extraField.isDeleteFieldRequest()) {
          continue;
        }
        rejectIfDuplicate(seen, extraField.getName());
      }
    }
  }

  /**
   * Validator-layer entry-point: walks the incoming fields[] and extraFields[] arrays and rejects
   * the SECOND occurrence of any case-insensitive name match. Errors are added with explicit nested
   * paths (e.g. {@code fields[1].name}, {@code extraFields[0].name}) so API clients can pinpoint
   * the problematic entry. Used from the Spring {@link org.springframework.validation.Validator}s
   * before the request reaches the manager.
   *
   * <p>Comparison is case-insensitive after trimming, matching the manager-layer paths.
   *
   * <p>Like the manager-layer methods, this entry-point does NOT enforce collisions with displayed
   * labels — that is owned by core-model.
   */
  public static void rejectDuplicatesInPayload(
      InventoryRecord record,
      List<ApiInventoryEntityField> fields,
      List<ApiExtraField> extraFields,
      Errors errors) {
    Set<String> seen = new HashSet<>();
    if (fields != null) {
      for (int i = 0; i < fields.size(); i++) {
        ApiInventoryEntityField field = fields.get(i);
        if (field.isDeleteFieldRequest()) {
          continue;
        }
        rejectIfDuplicate(seen, field.getName(), "fields[" + i + "].name", errors);
      }
    }
    if (extraFields != null) {
      for (int i = 0; i < extraFields.size(); i++) {
        ApiExtraField extraField = extraFields.get(i);
        rejectIfDuplicate(seen, extraField.getName(), "extraFields[" + i + "].name", errors);
      }
    }
  }

  private static void rejectIfDuplicate(Set<String> seen, String rawName) {
    if (StringUtils.isBlank(rawName)) {
      return;
    }
    String trimmed = rawName.trim();
    if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
      throw new ApiRuntimeException(DUPLICATE_NAME_ERROR_CODE, trimmed);
    }
  }

  private static void rejectIfDuplicate(
      Set<String> seen, String rawName, String fieldPath, Errors errors) {
    if (StringUtils.isBlank(rawName)) {
      return;
    }
    String trimmed = rawName.trim();
    if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
      errors.rejectValue(
          fieldPath,
          DUPLICATE_NAME_ERROR_CODE,
          new Object[] {trimmed},
          "duplicate field name '" + trimmed + "'");
    }
  }
}
