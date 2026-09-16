package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.record.BaseRecord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds the fields an operation generates, and keeps their names unique and storable. */
public final class OperationFieldNames {

  /** Fixed key, not an operation's own: the documentation link is the wizard's. */
  public static final String DOCUMENTATION_LINK_KEY = "operations.documentationLink";

  private OperationFieldNames() {}

  public static ApiExtraField link(
      String name, String fieldKey, String relationType, String targetGlobalId) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName(name);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(fieldKey);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(relationType);
    link.setTargetGlobalId(targetGlobalId);
    field.setLink(link);
    return field;
  }

  public static ApiExtraField text(String name, String fieldKey, String content) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName(name);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(fieldKey);
    field.setContent(content == null ? "" : content);
    return field;
  }

  /**
   * Suffixes each name in a colliding group to make it unique - a link by the target global id,
   * everything else by an ordinal - since two pooled origins sharing a name previously produced
   * duplicate field names and a guaranteed 400.
   */
  public static List<ApiExtraField> withUniqueFieldNames(List<ApiExtraField> fields) {
    Map<String, Integer> occurrences = new HashMap<>();
    for (ApiExtraField field : fields) {
      occurrences.merge(comparable(field.getName()), 1, Integer::sum);
    }
    Set<String> used = new HashSet<>();
    for (ApiExtraField field : fields) {
      String resolved = field.getName();
      String suffix =
          occurrences.get(comparable(resolved)) > 1
                  && field.getType() == ApiExtraField.ExtraFieldTypeEnum.LINK
              ? " (" + field.getLink().getTargetGlobalId() + ")"
              : "";
      String candidate = fit(resolved, suffix.length()) + suffix;
      for (int ordinal = 2; used.contains(comparable(candidate)); ordinal++) {
        String ordinalSuffix = suffix + " (" + ordinal + ")";
        candidate = fit(resolved, ordinalSuffix.length()) + ordinalSuffix;
      }
      used.add(comparable(candidate));
      field.setName(candidate);
    }
    return fields;
  }

  /**
   * Truncates a composed name to leave {@code reserve} characters for the uniqueness suffix.
   * Generated names can interpolate an origin's own name, which may already be at the 255-char
   * column limit; without this bound, an over-long name failed at the INSERT as a 500 after the
   * origins had already been decremented. Truncated rather than rejected because pooling near-limit
   * names must stay possible and only the display name is affected, not the link target. Cutting
   * before the suffix, not after, keeps the suffix that makes the name unique.
   */
  static String fit(String name, int reserve) {
    int room = Math.max(0, BaseRecord.DEFAULT_VARCHAR_LENGTH - reserve);
    return name.length() <= room ? name : name.substring(0, room).stripTrailing();
  }

  private static String comparable(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }
}
